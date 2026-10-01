package com.ektrepha.admin.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.admin.dto.request.AdminNannyCreateRequest;
import com.ektrepha.admin.dto.request.AdminNannyUpdateRequest;
import com.ektrepha.admin.dto.response.AdminBookingListResponse;
import com.ektrepha.admin.dto.response.AdminBookingSummaryResponse;
import com.ektrepha.admin.dto.response.AdminNannyDetailResponse;
import com.ektrepha.admin.dto.response.AdminNannyListResponse;
import com.ektrepha.admin.dto.response.AdminNannyReviewListResponse;
import com.ektrepha.admin.dto.response.AdminNannyReviewResponse;
import com.ektrepha.admin.dto.response.AdminNannySummaryResponse;
import com.ektrepha.admin.dto.response.AdminNannyVerificationDocumentResponse;
import com.ektrepha.admin.dto.response.AdminNannyZoneMappingResponse;
import com.ektrepha.admin.service.AdminNannyService;
import com.ektrepha.exception.DuplicateAccountException;
import com.ektrepha.exception.NannyNotFoundException;
import com.ektrepha.exception.ServiceTypeNotFoundException;
import com.ektrepha.exception.ZoneNotFoundException;
import com.ektrepha.model.Booking;
import com.ektrepha.model.CaregiverZoneMapping;
import com.ektrepha.model.Nanny;
import com.ektrepha.model.NannyVerificationStatus;
import com.ektrepha.model.Review;
import com.ektrepha.model.ServiceType;
import com.ektrepha.model.User;
import com.ektrepha.model.UserSource;
import com.ektrepha.model.UserType;
import com.ektrepha.model.VerificationDocType;
import com.ektrepha.model.ZoneArea;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.CaregiverZoneMappingRepository;
import com.ektrepha.repository.NannyRepository;
import com.ektrepha.repository.NannyVerificationRepository;
import com.ektrepha.repository.ReviewRepository;
import com.ektrepha.repository.ServiceTypeRepository;
import com.ektrepha.repository.UserRepository;
import com.ektrepha.repository.ZoneAreaRepository;
import com.ektrepha.storage.S3PhotoUrlService;
import com.ektrepha.verification.service.NannyVerificationService;

import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminNannyServiceImpl implements AdminNannyService {

	private final NannyRepository nannyRepository;
	private final UserRepository userRepository;
	private final CaregiverZoneMappingRepository caregiverZoneMappingRepository;
	private final ZoneAreaRepository zoneAreaRepository;
	private final ServiceTypeRepository serviceTypeRepository;
	private final NannyVerificationRepository nannyVerificationRepository;
	private final ReviewRepository reviewRepository;
	private final S3PhotoUrlService s3PhotoUrlService;
	private final BookingRepository bookingRepository;
	private final NannyVerificationService nannyVerificationService;

	@Override
	@Transactional(readOnly = true)
	public AdminNannyListResponse list(String q, NannyVerificationStatus verificationStatus, Boolean active, Long zoneAreaId, int page, int size) {
		String likeQ = (q == null || q.isBlank()) ? null : "%" + q.trim().toLowerCase() + "%";
		Page<Nanny> nannies = nannyRepository.searchForAdmin(likeQ, verificationStatus, active, zoneAreaId, PageRequest.of(page, size));

		List<Long> nannyIds = nannies.getContent().stream().map(Nanny::getId).toList();
		Map<Long, List<String>> zoneNamesByNannyId = new HashMap<>();
		if (!nannyIds.isEmpty()) {
			for (Object[] row : caregiverZoneMappingRepository.findActiveZoneNamesByCaregiverIds(nannyIds)) {
				zoneNamesByNannyId.computeIfAbsent((Long) row[0], k -> new ArrayList<>()).add((String) row[1]);
			}
		}

		List<AdminNannySummaryResponse> items = nannies.getContent().stream()
				.map(n -> new AdminNannySummaryResponse(
						n.getId(), AdminBookingMapper.fullName(n.getFirstName(), n.getLastName()), n.getUser().getPhone(), n.getUser().getEmail(),
						n.getOverallVerificationStatus().name(), n.getUser().isActive(), n.getHourlyRate(),
						zoneNamesByNannyId.getOrDefault(n.getId(), List.of()), n.getCreatedAt()))
				.toList();

		return new AdminNannyListResponse(items, nannies.getNumber(), nannies.getSize(), nannies.getTotalElements(), nannies.getTotalPages());
	}

	@Override
	@Transactional(readOnly = true)
	public AdminNannyDetailResponse detail(Long id) {
		Nanny nanny = nannyRepository.findByIdWithUser(id)
				.orElseThrow(() -> new NannyNotFoundException("No nanny with id " + id));
		return toDetail(nanny);
	}

	@Override
	@Transactional
	public AdminNannyDetailResponse create(AdminNannyCreateRequest request) {
		String phone = normalizePhone(request.phone());
		if (userRepository.existsByPhone(phone)) {
			throw DuplicateAccountException.phone(phone);
		}
		if (request.email() != null && !request.email().isBlank() && userRepository.existsByEmail(request.email())) {
			throw DuplicateAccountException.email(request.email());
		}

		User user = userRepository.save(User.builder()
				.name((request.firstName() + " " + (request.lastName() == null ? "" : request.lastName())).trim())
				.phone(phone)
				.email(request.email())
				.userType(UserType.NANNY)
				.userSource(UserSource.PHONE)
				.active(true)
				.phoneVerified(false)
				.emailVerified(false)
				.build());

		Nanny nanny = Nanny.builder()
				.user(user)
				.firstName(request.firstName())
				.lastName(request.lastName())
				.educationLevel(request.educationLevel())
				.yearsExperience(request.yearsExperience())
				.hourlyRate(request.hourlyRate())
				.overallVerificationStatus(NannyVerificationStatus.PENDING)
				.build();
		nanny = nannyRepository.save(nanny);

		if (request.zoneAreaId() != null && request.serviceTypeId() != null) {
			ZoneArea zone = zoneAreaRepository.findById(request.zoneAreaId())
					.orElseThrow(() -> new ZoneNotFoundException("No zone with id " + request.zoneAreaId()));
			ServiceType serviceType = serviceTypeRepository.findById(request.serviceTypeId())
					.orElseThrow(() -> new ServiceTypeNotFoundException("No service type with id " + request.serviceTypeId()));
			caregiverZoneMappingRepository.save(CaregiverZoneMapping.builder()
					.caregiver(nanny).zoneArea(zone).serviceType(serviceType)
					.ownRate(request.ownRate()).active(true).build());
		}

		return toDetail(nannyRepository.findByIdWithUser(nanny.getId()).orElseThrow());
	}

	@Override
	@Transactional
	public AdminNannyDetailResponse update(Long id, AdminNannyUpdateRequest request) {
		Nanny nanny = nannyRepository.findByIdWithUser(id)
				.orElseThrow(() -> new NannyNotFoundException("No nanny with id " + id));

		if (request.active() != null) {
			nanny.getUser().setActive(request.active());
		}
		if (request.hourlyRate() != null) {
			nanny.setHourlyRate(request.hourlyRate());
		}
		if (request.educationLevel() != null) {
			nanny.setEducationLevel(request.educationLevel());
		}
		if (request.yearsExperience() != null) {
			nanny.setYearsExperience(request.yearsExperience());
		}
		userRepository.save(nanny.getUser());
		nanny = nannyRepository.save(nanny);

		return toDetail(nanny);
	}

	@Override
	@Transactional(readOnly = true)
	public List<AdminNannyVerificationDocumentResponse> documents(Long nannyId) {
		if (!nannyRepository.existsById(nannyId)) {
			throw new NannyNotFoundException("No nanny with id " + nannyId);
		}
		return nannyVerificationRepository.findByNannyId(nannyId).stream()
				.map(v -> new AdminNannyVerificationDocumentResponse(v.getId(), v.getType().name(), v.getStatus().name(),
						v.getCreatedAt(), v.getReviewedAt(), v.getRejectionReason(), s3PhotoUrlService.presign(v.getS3Key())))
				.toList();
	}

	@Override
	@Transactional
	public AdminNannyVerificationDocumentResponse uploadDocument(Long nannyId, VerificationDocType type, MultipartFile file) {
		var record = nannyVerificationService.submitDocumentForNanny(nannyId, type, file);
		return new AdminNannyVerificationDocumentResponse(record.getId(), record.getType().name(), record.getStatus().name(),
				record.getCreatedAt(), record.getReviewedAt(), record.getRejectionReason(), s3PhotoUrlService.presign(record.getS3Key()));
	}

	@Override
	@Transactional(readOnly = true)
	public AdminBookingListResponse roster(Long nannyId, int page, int size) {
		if (!nannyRepository.existsById(nannyId)) {
			throw new NannyNotFoundException("No nanny with id " + nannyId);
		}
		Page<Booking> bookings = bookingRepository.findByNannyIdForAdmin(nannyId, PageRequest.of(page, size));
		List<AdminBookingSummaryResponse> items = bookings.getContent().stream()
				.map(AdminBookingMapper::toSummary)
				.toList();
		return new AdminBookingListResponse(items, bookings.getNumber(), bookings.getSize(), bookings.getTotalElements(), bookings.getTotalPages());
	}

	@Override
	@Transactional(readOnly = true)
	public AdminNannyReviewListResponse reviews(Long nannyId, int page, int size) {
		if (!nannyRepository.existsById(nannyId)) {
			throw new NannyNotFoundException("No nanny with id " + nannyId);
		}
		Page<Review> reviews = reviewRepository.findByNannyIdOrderByCreatedAtDesc(nannyId, PageRequest.of(page, size));
		List<AdminNannyReviewResponse> items = reviews.getContent().stream()
				.map(r -> new AdminNannyReviewResponse(r.getId(), r.getBooking().getId(),
						AdminBookingMapper.resolveParentName(r.getBooking()), r.getRating(), r.getComment(), r.getCreatedAt()))
				.toList();
		return new AdminNannyReviewListResponse(items, reviews.getNumber(), reviews.getSize(), reviews.getTotalElements(), reviews.getTotalPages());
	}

	private AdminNannyDetailResponse toDetail(Nanny nanny) {
		List<AdminNannyZoneMappingResponse> zoneMappings = caregiverZoneMappingRepository.findByCaregiverIdWithZoneAndServiceType(nanny.getId()).stream()
				.map(czm -> new AdminNannyZoneMappingResponse(czm.getZoneArea().getId(), czm.getZoneArea().getName(),
						czm.getServiceType().getId(), czm.getServiceType().getCode(), czm.getOwnRate(), czm.isActive()))
				.toList();

		List<Object[]> ratingAggregate = reviewRepository.findRatingAggregate(nanny.getId());
		Double ratingAvg = null;
		long reviewCount = 0;
		if (!ratingAggregate.isEmpty()) {
			Object[] row = ratingAggregate.get(0);
			ratingAvg = (Double) row[0];
			reviewCount = (Long) row[1];
		}

		return new AdminNannyDetailResponse(
				nanny.getId(), nanny.getFirstName(), nanny.getLastName(), nanny.getUser().getPhone(), nanny.getUser().getEmail(),
				nanny.getBio(), nanny.getEducationLevel(), nanny.getYearsExperience(), nanny.getHourlyRate(),
				nanny.getOverallVerificationStatus().name(), nanny.getUser().isActive(), ratingAvg, reviewCount,
				zoneMappings, nanny.getCreatedAt());
	}

	// Mirrors StaffAuthServiceImpl#normalizePhone / MobileOtpServiceImpl's own copy — same tiny
	// snippet duplicated per-class in this codebase rather than pulled into a shared util.
	private String normalizePhone(String phoneNumber) {
		String trimmed = phoneNumber.trim();
		if (trimmed.startsWith("+")) {
			return trimmed;
		}
		String digits = trimmed.replaceAll("\\D", "");
		return digits.length() == 10 ? "+91" + digits : trimmed;
	}

}
