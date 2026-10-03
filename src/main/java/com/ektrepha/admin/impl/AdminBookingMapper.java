package com.ektrepha.admin.impl;

import com.ektrepha.admin.dto.response.AdminBookingSummaryResponse;
import com.ektrepha.admin.dto.response.AdminLiveShiftResponse;
import com.ektrepha.model.Booking;

/** Shared row-mapping between {@link AdminBookingServiceImpl} and {@link AdminDashboardServiceImpl} — both render the same booking shape in different lists. */
final class AdminBookingMapper {

	private AdminBookingMapper() {
	}

	static AdminBookingSummaryResponse toSummary(Booking booking) {
		return new AdminBookingSummaryResponse(
				booking.getId(), booking.getServiceType().getCode(), booking.getServiceType().getName(),
				resolveParentName(booking), booking.getAddress() == null ? null : booking.getAddress().getCity(),
				booking.getChild() == null ? 0 : 1,
				booking.getNanny() == null ? null : booking.getNanny().getId(),
				booking.getNanny() == null ? null : fullName(booking.getNanny().getFirstName(), booking.getNanny().getLastName()),
				booking.getStartTime(), booking.getEndTime(), booking.getStatus().name(),
				booking.getCheckedInAt(), booking.getCheckedOutAt(), null);
	}

	static AdminLiveShiftResponse toLiveShift(Booking booking) {
		return new AdminLiveShiftResponse(
				booking.getId(),
				booking.getNanny() == null ? null : booking.getNanny().getId(),
				booking.getNanny() == null ? null : fullName(booking.getNanny().getFirstName(), booking.getNanny().getLastName()),
				resolveParentName(booking),
				booking.getAddress() == null ? null : booking.getAddress().getCity(),
				booking.getStartTime(), booking.getEndTime(), booking.getCheckedInAt());
	}

	// Parent.firstName/lastName is nullable (auto-vivified stub rows, see Parent's own comment) —
	// falls back to the account's own name.
	static String resolveParentName(Booking booking) {
		String name = fullName(booking.getParent().getFirstName(), booking.getParent().getLastName());
		return (name == null || name.isBlank()) ? booking.getParent().getUser().getName() : name;
	}

	static String fullName(String firstName, String lastName) {
		if (firstName == null && lastName == null) {
			return null;
		}
		return (firstName == null ? "" : firstName) + (lastName == null ? "" : " " + lastName);
	}

}
