package com.ektrepha.nannysearch.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.config.properties.AppProperties;
import com.ektrepha.exception.ForbiddenChildAccessException;
import com.ektrepha.exception.InvalidSearchParametersException;
import com.ektrepha.exception.ParentAddressNotFoundException;
import com.ektrepha.exception.UserNotFoundException;
import com.ektrepha.model.Parent;
import com.ektrepha.model.ParentAddress;
import com.ektrepha.nannysearch.dto.request.NannySearchRequest;
import com.ektrepha.nannysearch.dto.response.LanguageOptionResponse;
import com.ektrepha.nannysearch.dto.response.NannySearchResponse;
import com.ektrepha.nannysearch.dto.response.NannySearchResultItem;
import com.ektrepha.nannysearch.dto.response.SkillOptionResponse;
import com.ektrepha.nannysearch.service.NannyRankingService.RankedCandidate;
import com.ektrepha.nannysearch.service.NannyRankingService;
import com.ektrepha.nannysearch.service.NannySearchService;
import com.ektrepha.repository.LanguageRepository;
import com.ektrepha.repository.NannySearchRepository;
import com.ektrepha.repository.ParentAddressRepository;
import com.ektrepha.repository.ParentChildRepository;
import com.ektrepha.repository.ParentRepository;
import com.ektrepha.repository.SkillRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NannySearchServiceImpl implements NannySearchService {

	private final ParentRepository parentRepository;
	private final ParentAddressRepository parentAddressRepository;
	private final ParentChildRepository parentChildRepository;
	private final LanguageRepository languageRepository;
	private final SkillRepository skillRepository;
	private final NannySearchRepository nannySearchRepository;
	private final NannyRankingService nannyRankingService;
	private final AppProperties appProperties;

	// Validates radius/time-window (cheap, no DB access — fail fast before any lookup), resolves
	// the calling user to their Parent record, validates child ownership, resolves search
	// coordinates, runs the candidate query, ranks the results, and returns a paginated response slice.
	@Override
	@Transactional(readOnly = true)
	public NannySearchResponse search(Long userId, NannySearchRequest request) {
		assertValidRadius(request.radiusKm());
		assertValidWindow(request.windowStart(), request.windowEnd());
		Parent parent = resolveParent(userId);
		assertChildBelongsToParent(parent.getId(), request.childId());

		double[] location = resolveSearchLocation(parent, request);

		NannySearchRepository.Criteria criteria = new NannySearchRepository.Criteria(
				location[0],
				location[1],
				request.radiusKm(),
				request.windowStart(),
				request.windowEnd(),
				request.minPrice(),
				request.maxPrice(),
				request.minYearsExperience(),
				request.educationLevel(),
				request.languageIds(),
				request.skillIds(),
				appProperties.search().candidateFetchLimit());

		List<NannySearchRepository.CandidateRow> candidates = nannySearchRepository.findCandidates(criteria);
		List<RankedCandidate> ranked = nannyRankingService.rank(candidates, request);

		int page = request.page() != null ? request.page() : 0;
		int pageSize = request.pageSize() != null ? request.pageSize() : appProperties.search().defaultPageSize();
		List<NannySearchResultItem> pageItems = sliceAndMap(ranked, page, pageSize);

		return new NannySearchResponse(pageItems, page, pageSize, ranked.size());
	}

	// Lists active catalog languages for populating the search form's language filter.
	@Override
	@Transactional(readOnly = true)
	public List<LanguageOptionResponse> listActiveLanguages() {
		return languageRepository.findByActiveTrueOrderByNameAsc().stream()
				.map(language -> new LanguageOptionResponse(language.getId(), language.getName()))
				.toList();
	}

	// Lists the full skill catalog for populating the search form's skills filter.
	@Override
	@Transactional(readOnly = true)
	public List<SkillOptionResponse> listSkills() {
		return skillRepository.findAllByOrderByNameAsc().stream()
				.map(skill -> new SkillOptionResponse(skill.getId(), skill.getName()))
				.toList();
	}

	// Looks up the Parent profile for an authenticated user id — every PARENT-role account should
	// have one, so a miss here indicates a data-integrity gap, not a normal user error.
	private Parent resolveParent(Long userId) {
		return parentRepository.findByUserId(userId)
				.orElseThrow(() -> new UserNotFoundException("No parent profile found for this account"));
	}

	// Radius must be one of the operator-configured allowed values, not just any positive number.
	private void assertValidRadius(int radiusKm) {
		if (!appProperties.search().allowedRadiiKm().contains(radiusKm)) {
			throw new InvalidSearchParametersException(
					"radiusKm must be one of " + appProperties.search().allowedRadiiKm());
		}
	}

	// The search window must be well-formed — end strictly after start.
	private void assertValidWindow(Instant windowStart, Instant windowEnd) {
		if (!windowEnd.isAfter(windowStart)) {
			throw new InvalidSearchParametersException("windowEnd must be after windowStart");
		}
	}

	// A parent may only search on behalf of a child they actually have a parent_child row for.
	private void assertChildBelongsToParent(Long parentId, Long childId) {
		if (!parentChildRepository.existsByIdParentIdAndIdChildId(parentId, childId)) {
			throw new ForbiddenChildAccessException("childId does not belong to the requesting parent");
		}
	}

	// Resolution order: an explicit lat/lng override, then a named saved address (ownership
	// checked), then the parent's primary address. Returns {lat, lng}.
	private double[] resolveSearchLocation(Parent parent, NannySearchRequest request) {
		if (request.lat() != null && request.lng() != null) {
			return new double[] { request.lat(), request.lng() };
		}
		if (request.parentAddressId() != null) {
			ParentAddress address = parentAddressRepository.findById(request.parentAddressId())
					.filter(a -> a.getParent().getId().equals(parent.getId()))
					.orElseThrow(() -> new ParentAddressNotFoundException("No such address for this parent"));
			return toLatLng(address);
		}
		ParentAddress primary = parentAddressRepository.findByParentIdAndPrimaryTrue(parent.getId())
				.orElseThrow(() -> new ParentAddressNotFoundException("Parent has no primary address and none was supplied"));
		return toLatLng(primary);
	}

	private double[] toLatLng(ParentAddress address) {
		if (address.getLat() == null || address.getLng() == null) {
			throw new ParentAddressNotFoundException("Selected address has no coordinates on file");
		}
		return new double[] { address.getLat(), address.getLng() };
	}

	// Applies in-memory pagination over the already-ranked list, then maps to the public response shape.
	private List<NannySearchResultItem> sliceAndMap(List<RankedCandidate> ranked, int page, int pageSize) {
		int fromIndex = Math.min(page * pageSize, ranked.size());
		int toIndex = Math.min(fromIndex + pageSize, ranked.size());
		return ranked.subList(fromIndex, toIndex).stream()
				.map(this::toResultItem)
				.toList();
	}

	private NannySearchResultItem toResultItem(RankedCandidate ranked) {
		NannySearchRepository.CandidateRow candidate = ranked.candidate();
		return new NannySearchResultItem(
				candidate.nannyId(),
				candidate.firstName(),
				candidate.lastName(),
				null,
				candidate.hourlyRate(),
				candidate.yearsExperience(),
				candidate.educationLevel(),
				candidate.distanceM(),
				ranked.totalScore());
	}

}
