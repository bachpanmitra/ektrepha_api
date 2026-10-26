package com.ektrepha.hourlycare.service;

import com.ektrepha.hourlycare.dto.request.AssignCaregiverRequest;
import com.ektrepha.hourlycare.dto.request.AvailabilityCheckRequest;
import com.ektrepha.hourlycare.dto.request.HourlyCareBookingCreateRequest;
import com.ektrepha.hourlycare.dto.request.LiveInAvailabilityCheckRequest;
import com.ektrepha.hourlycare.dto.request.LiveInBookingCreateRequest;
import com.ektrepha.hourlycare.dto.request.MonthlyAvailabilityCheckRequest;
import com.ektrepha.hourlycare.dto.request.MonthlyBookingCreateRequest;
import com.ektrepha.hourlycare.dto.request.PaymentConfirmRequest;
import com.ektrepha.hourlycare.dto.request.PaymentInitiateRequest;
import com.ektrepha.hourlycare.dto.request.ReassignCaregiverRequest;
import com.ektrepha.hourlycare.dto.response.AvailabilityResponse;
import com.ektrepha.hourlycare.dto.response.BookingStatusResponse;
import com.ektrepha.hourlycare.dto.response.CaregiverAssignmentResponse;
import com.ektrepha.hourlycare.dto.response.HourlyCareBookingResponse;
import com.ektrepha.hourlycare.dto.response.HourlyCareQuoteResponse;
import com.ektrepha.hourlycare.dto.response.LiveInAvailabilityResponse;
import com.ektrepha.hourlycare.dto.response.LiveInBookingResponse;
import com.ektrepha.hourlycare.dto.response.MonthlyAvailabilityResponse;
import com.ektrepha.hourlycare.dto.response.MonthlyBookingResponse;
import com.ektrepha.hourlycare.dto.response.PaymentInitiateResponse;
import com.ektrepha.pricing.dto.response.PriceQuoteResponse;

public interface HourlyCareService {

	AvailabilityResponse checkAvailability(Long userId, AvailabilityCheckRequest request);

	// Same inputs as checkAvailability, minus the capacity check - just the price.
	PriceQuoteResponse getPrice(Long userId, AvailabilityCheckRequest request);

	// Locks today's price (including any FIRST5_FEE_WAIVER trial discount) for app.trial.quote-lock-minutes
	// (default 15) behind a quoteId createBooking can honor. Same inputs as checkAvailability/getPrice.
	HourlyCareQuoteResponse quote(Long userId, AvailabilityCheckRequest request);

	HourlyCareBookingResponse createBooking(Long userId, HourlyCareBookingCreateRequest request);

	// Monthly care - a bounded recurring series on chosen weekdays. Pays and settles through the
	// exact same initiatePayment/confirmPayment/status methods below, keyed on the anchor booking's id.
	MonthlyAvailabilityResponse checkMonthlyAvailability(Long userId, MonthlyAvailabilityCheckRequest request);

	MonthlyBookingResponse createMonthlyBooking(Long userId, MonthlyBookingCreateRequest request);

	// Live-in care - a helper living in the home for whole months, one booking row per on-duty day
	// (see LiveInArrangement). Pays/settles through the same initiatePayment/confirmPayment/status
	// below as monthly care, keyed on the anchor day's booking id.
	LiveInAvailabilityResponse checkLiveInAvailability(Long userId, LiveInAvailabilityCheckRequest request);

	LiveInBookingResponse createLiveInBooking(Long userId, LiveInBookingCreateRequest request);

	// Any day of the stay (or its anchor) resolves to the whole stay.
	LiveInBookingResponse liveInBooking(Long userId, Long bookingId);

	PaymentInitiateResponse initiatePayment(Long userId, Long bookingId, PaymentInitiateRequest request);

	BookingStatusResponse confirmPayment(Long userId, Long paymentId, PaymentConfirmRequest request);

	BookingStatusResponse failPayment(Long userId, Long paymentId);

	BookingStatusResponse status(Long userId, Long bookingId);

	// Ops-only (ADMIN role) - adminUserId isn't a scoping filter like the parent-facing methods
	// above, it's just who to attribute the CAREGIVER_ASSIGNED activity-log row to. On a live-in
	// day, assigns the same helper to every day of the stay at once.
	CaregiverAssignmentResponse assignCaregiver(Long adminUserId, Long bookingId, AssignCaregiverRequest request);

	// Emergency-replacement flow: swaps the caregiver on a booking that's already CONFIRMED (not
	// awaiting assignment like assignCaregiver above) - a no-show or last-minute cancellation. On a
	// live-in day, swaps that day and every later not-yet-started day of the stay.
	CaregiverAssignmentResponse reassignCaregiver(Long adminUserId, Long bookingId, ReassignCaregiverRequest request);

}
