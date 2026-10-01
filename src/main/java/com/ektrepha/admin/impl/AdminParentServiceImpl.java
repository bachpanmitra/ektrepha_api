package com.ektrepha.admin.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.admin.dto.response.AdminBookingSummaryResponse;
import com.ektrepha.admin.dto.response.AdminParentDetailResponse;
import com.ektrepha.admin.dto.response.AdminParentListResponse;
import com.ektrepha.admin.dto.response.AdminParentSummaryResponse;
import com.ektrepha.admin.service.AdminParentService;
import com.ektrepha.exception.ParentNotFoundException;
import com.ektrepha.model.Parent;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.ParentChildRepository;
import com.ektrepha.repository.ParentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminParentServiceImpl implements AdminParentService {

	private final ParentRepository parentRepository;
	private final ParentChildRepository parentChildRepository;
	private final BookingRepository bookingRepository;

	@Override
	@Transactional(readOnly = true)
	public AdminParentListResponse list(String q, int page, int size) {
		String likeQ = (q == null || q.isBlank()) ? null : "%" + q.trim().toLowerCase() + "%";
		Page<Parent> parents = parentRepository.searchForAdmin(likeQ, PageRequest.of(page, size));

		List<Long> parentIds = parents.getContent().stream().map(Parent::getId).toList();
		Map<Long, Long> childrenByParentId = new HashMap<>();
		Map<Long, Long> bookingsByParentId = new HashMap<>();
		if (!parentIds.isEmpty()) {
			for (Object[] row : parentChildRepository.countChildrenByParentIds(parentIds)) {
				childrenByParentId.put((Long) row[0], (Long) row[1]);
			}
			for (Object[] row : bookingRepository.countBookingsByParentIds(parentIds)) {
				bookingsByParentId.put((Long) row[0], (Long) row[1]);
			}
		}

		List<AdminParentSummaryResponse> items = parents.getContent().stream()
				.map(p -> new AdminParentSummaryResponse(
						p.getId(), resolveName(p), p.getUser().getEmail(), p.getUser().getPhone(),
						childrenByParentId.getOrDefault(p.getId(), 0L), bookingsByParentId.getOrDefault(p.getId(), 0L), p.getCreatedAt()))
				.toList();

		return new AdminParentListResponse(items, parents.getNumber(), parents.getSize(), parents.getTotalElements(), parents.getTotalPages());
	}

	@Override
	@Transactional(readOnly = true)
	public AdminParentDetailResponse detail(Long id) {
		Parent parent = parentRepository.findByIdWithUser(id)
				.orElseThrow(() -> new ParentNotFoundException("No parent found with id " + id));

		long childrenCount = parentChildRepository.countByIdParentId(id);
		long bookingsCount = bookingRepository.countByParentId(id);
		List<AdminBookingSummaryResponse> recentBookings = bookingRepository.findRecentByParentId(id, PageRequest.of(0, 10))
				.stream().map(AdminBookingMapper::toSummary).toList();

		return new AdminParentDetailResponse(
				parent.getId(), resolveName(parent), parent.getUser().getEmail(), parent.getUser().getPhone(),
				parent.getUser().isEmailVerified(), parent.getUser().isPhoneVerified(),
				childrenCount, bookingsCount, parent.getCreatedAt(), recentBookings);
	}

	private String resolveName(Parent parent) {
		String name = AdminBookingMapper.fullName(parent.getFirstName(), parent.getLastName());
		return (name == null || name.isBlank()) ? parent.getUser().getName() : name;
	}

}
