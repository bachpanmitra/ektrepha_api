package com.ektrepha.hourlycare.service;

import com.ektrepha.hourlycare.dto.request.AssignCaregiverRequest;
import com.ektrepha.hourlycare.dto.request.AvailabilityCheckRequest;
import com.ektrepha.hourlycare.dto.request.HourlyCareBookingCreateRequest;
import com.ektrepha.hourlycare.dto.request.PaymentInitiateRequest;
import com.ektrepha.hourlycare.dto.response.AvailabilityResponse;
import com.ektrepha.hourlycare.dto.response.BookingStatusResponse;
import com.ektrepha.hourlycare.dto.response.CaregiverAssignmentResponse;
import com.ektrepha.hourlycare.dto.response.HourlyCareBookingResponse;
import com.ektrepha.hourlycare.dto.response.PaymentInitiateResponse;

public interface HourlyCareService {

	AvailabilityResponse checkAvailability(Long userId, AvailabilityCheckRequest request);

	HourlyCareBookingResponse createBooking(Long userId, HourlyCareBookingCreateRequest request);

	PaymentInitiateResponse initiatePayment(Long userId, Long bookingId, PaymentInitiateRequest request);

	BookingStatusResponse confirmPayment(Long userId, Long paymentId);

	BookingStatusResponse failPayment(Long userId, Long paymentId);

	BookingStatusResponse status(Long userId, Long bookingId);

	// Ops-only (ADMIN role) - no userId scoping, unlike the parent-facing methods above.
	CaregiverAssignmentResponse assignCaregiver(Long bookingId, AssignCaregiverRequest request);

}
