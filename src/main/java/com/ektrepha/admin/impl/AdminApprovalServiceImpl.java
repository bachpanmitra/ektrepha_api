package com.ektrepha.admin.impl;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.admin.dto.request.AdminRejectionRequest;
import com.ektrepha.admin.dto.response.AdminAttendanceCorrectionListResponse;
import com.ektrepha.admin.dto.response.AdminAttendanceCorrectionResponse;
import com.ektrepha.admin.dto.response.AdminLeaveRequestListResponse;
import com.ektrepha.admin.dto.response.AdminLeaveRequestResponse;
import com.ektrepha.admin.dto.response.AdminShiftChangeRequestListResponse;
import com.ektrepha.admin.dto.response.AdminShiftChangeRequestResponse;
import com.ektrepha.admin.service.AdminApprovalService;
import com.ektrepha.exception.ApprovalRequestNotFoundException;
import com.ektrepha.exception.InvalidBookingTransitionException;
import com.ektrepha.exception.RequestAlreadyDecidedException;
import com.ektrepha.model.AttendanceCorrectionRequest;
import com.ektrepha.model.Booking;
import com.ektrepha.model.BookingStatus;
import com.ektrepha.model.LeaveRequest;
import com.ektrepha.model.RequestStatus;
import com.ektrepha.model.ShiftChangeRequest;
import com.ektrepha.model.User;
import com.ektrepha.repository.AttendanceCorrectionRequestRepository;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.LeaveRequestRepository;
import com.ektrepha.repository.ShiftChangeRequestRepository;
import com.ektrepha.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminApprovalServiceImpl implements AdminApprovalService {

	private final LeaveRequestRepository leaveRequestRepository;
	private final ShiftChangeRequestRepository shiftChangeRequestRepository;
	private final AttendanceCorrectionRequestRepository attendanceCorrectionRequestRepository;
	private final BookingRepository bookingRepository;
	private final UserRepository userRepository;

	@Override
	@Transactional(readOnly = true)
	public AdminLeaveRequestListResponse listLeaveRequests(RequestStatus status, Long nannyId, int page, int size) {
		Page<LeaveRequest> requests = leaveRequestRepository.searchForAdmin(status, nannyId, PageRequest.of(page, size));
		var items = requests.getContent().stream().map(this::toLeaveResponse).toList();
		return new AdminLeaveRequestListResponse(items, requests.getNumber(), requests.getSize(), requests.getTotalElements(), requests.getTotalPages());
	}

	@Override
	@Transactional
	public AdminLeaveRequestResponse approveLeaveRequest(Long id, Long reviewerUserId) {
		LeaveRequest request = findLeaveRequest(id);
		requirePending(request.getStatus());
		request.setStatus(RequestStatus.APPROVED);
		applyReview(request, reviewerUserId, null);
		return toLeaveResponse(leaveRequestRepository.save(request));
	}

	@Override
	@Transactional
	public AdminLeaveRequestResponse rejectLeaveRequest(Long id, Long reviewerUserId, AdminRejectionRequest rejectionRequest) {
		LeaveRequest request = findLeaveRequest(id);
		requirePending(request.getStatus());
		request.setStatus(RequestStatus.REJECTED);
		applyReview(request, reviewerUserId, rejectionRequest.reason());
		return toLeaveResponse(leaveRequestRepository.save(request));
	}

	@Override
	@Transactional(readOnly = true)
	public AdminShiftChangeRequestListResponse listShiftChangeRequests(RequestStatus status, int page, int size) {
		Page<ShiftChangeRequest> requests = shiftChangeRequestRepository.searchForAdmin(status, PageRequest.of(page, size));
		var items = requests.getContent().stream().map(this::toShiftChangeResponse).toList();
		return new AdminShiftChangeRequestListResponse(items, requests.getNumber(), requests.getSize(), requests.getTotalElements(), requests.getTotalPages());
	}

	@Override
	@Transactional
	public AdminShiftChangeRequestResponse approveShiftChangeRequest(Long id, Long reviewerUserId) {
		ShiftChangeRequest request = shiftChangeRequestRepository.findById(id)
				.orElseThrow(() -> new ApprovalRequestNotFoundException("No shift-change request with id " + id));
		requirePending(request.getStatus());

		Booking booking = request.getBooking();
		if (booking.getStatus() != BookingStatus.CONFIRMED) {
			throw new InvalidBookingTransitionException("This booking is no longer in a state a shift change can free up (status is " + booking.getStatus() + ")");
		}
		// Frees the shift back into the existing Assign screen's queue, per the admin spec's
		// "an approval that frees a shift links straight to its Assign screen".
		booking.setNanny(null);
		booking.setStatus(BookingStatus.ASSIGNING_CAREGIVER);
		bookingRepository.save(booking);

		request.setStatus(RequestStatus.APPROVED);
		applyReview(request, reviewerUserId, null);
		return toShiftChangeResponse(shiftChangeRequestRepository.save(request));
	}

	@Override
	@Transactional
	public AdminShiftChangeRequestResponse rejectShiftChangeRequest(Long id, Long reviewerUserId, AdminRejectionRequest rejectionRequest) {
		ShiftChangeRequest request = shiftChangeRequestRepository.findById(id)
				.orElseThrow(() -> new ApprovalRequestNotFoundException("No shift-change request with id " + id));
		requirePending(request.getStatus());
		request.setStatus(RequestStatus.REJECTED);
		applyReview(request, reviewerUserId, rejectionRequest.reason());
		return toShiftChangeResponse(shiftChangeRequestRepository.save(request));
	}

	@Override
	@Transactional(readOnly = true)
	public AdminAttendanceCorrectionListResponse listAttendanceCorrections(RequestStatus status, int page, int size) {
		Page<AttendanceCorrectionRequest> requests = attendanceCorrectionRequestRepository.searchForAdmin(status, PageRequest.of(page, size));
		var items = requests.getContent().stream().map(this::toAttendanceResponse).toList();
		return new AdminAttendanceCorrectionListResponse(items, requests.getNumber(), requests.getSize(), requests.getTotalElements(), requests.getTotalPages());
	}

	@Override
	@Transactional
	public AdminAttendanceCorrectionResponse approveAttendanceCorrection(Long id, Long reviewerUserId) {
		AttendanceCorrectionRequest request = findAttendanceCorrection(id);
		requirePending(request.getStatus());
		request.setStatus(RequestStatus.APPROVED);
		applyReview(request, reviewerUserId, null);
		return toAttendanceResponse(attendanceCorrectionRequestRepository.save(request));
	}

	@Override
	@Transactional
	public AdminAttendanceCorrectionResponse rejectAttendanceCorrection(Long id, Long reviewerUserId, AdminRejectionRequest rejectionRequest) {
		AttendanceCorrectionRequest request = findAttendanceCorrection(id);
		requirePending(request.getStatus());
		request.setStatus(RequestStatus.REJECTED);
		applyReview(request, reviewerUserId, rejectionRequest.reason());
		return toAttendanceResponse(attendanceCorrectionRequestRepository.save(request));
	}

	@Override
	@Transactional(readOnly = true)
	public long countPending() {
		return leaveRequestRepository.countByStatus(RequestStatus.PENDING)
				+ shiftChangeRequestRepository.countByStatus(RequestStatus.PENDING)
				+ attendanceCorrectionRequestRepository.countByStatus(RequestStatus.PENDING);
	}

	private LeaveRequest findLeaveRequest(Long id) {
		return leaveRequestRepository.findById(id)
				.orElseThrow(() -> new ApprovalRequestNotFoundException("No leave request with id " + id));
	}

	private AttendanceCorrectionRequest findAttendanceCorrection(Long id) {
		return attendanceCorrectionRequestRepository.findById(id)
				.orElseThrow(() -> new ApprovalRequestNotFoundException("No attendance correction request with id " + id));
	}

	private void requirePending(RequestStatus status) {
		if (status != RequestStatus.PENDING) {
			throw new RequestAlreadyDecidedException("This request has already been " + status.name().toLowerCase());
		}
	}

	private void applyReview(LeaveRequest request, Long reviewerUserId, String rejectionReason) {
		request.setReviewedAt(Instant.now());
		request.setRejectionReason(rejectionReason);
		findReviewer(reviewerUserId).ifPresent(request::setReviewedBy);
	}

	private void applyReview(ShiftChangeRequest request, Long reviewerUserId, String rejectionReason) {
		request.setReviewedAt(Instant.now());
		request.setRejectionReason(rejectionReason);
		findReviewer(reviewerUserId).ifPresent(request::setReviewedBy);
	}

	private void applyReview(AttendanceCorrectionRequest request, Long reviewerUserId, String rejectionReason) {
		request.setReviewedAt(Instant.now());
		request.setRejectionReason(rejectionReason);
		findReviewer(reviewerUserId).ifPresent(request::setReviewedBy);
	}

	private Optional<User> findReviewer(Long reviewerUserId) {
		return reviewerUserId == null ? Optional.empty() : userRepository.findById(reviewerUserId);
	}

	private AdminLeaveRequestResponse toLeaveResponse(LeaveRequest r) {
		return new AdminLeaveRequestResponse(r.getId(), r.getNanny().getId(), AdminBookingMapper.fullName(r.getNanny().getFirstName(), r.getNanny().getLastName()),
				r.getStartDate(), r.getEndDate(), r.getReason(), r.getStatus().name(), r.getReviewedAt(), r.getRejectionReason(), r.getCreatedAt());
	}

	private AdminShiftChangeRequestResponse toShiftChangeResponse(ShiftChangeRequest r) {
		return new AdminShiftChangeRequestResponse(r.getId(), r.getBooking().getId(), r.getNanny().getId(),
				AdminBookingMapper.fullName(r.getNanny().getFirstName(), r.getNanny().getLastName()),
				r.getReason(), r.getStatus().name(), r.getReviewedAt(), r.getRejectionReason(), r.getCreatedAt());
	}

	private AdminAttendanceCorrectionResponse toAttendanceResponse(AttendanceCorrectionRequest r) {
		return new AdminAttendanceCorrectionResponse(r.getId(), r.getBooking().getId(), r.getNanny().getId(),
				AdminBookingMapper.fullName(r.getNanny().getFirstName(), r.getNanny().getLastName()),
				r.getDetails(), r.getStatus().name(), r.getReviewedAt(), r.getRejectionReason(), r.getCreatedAt());
	}

}
