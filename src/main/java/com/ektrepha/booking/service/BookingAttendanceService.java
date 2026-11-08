package com.ektrepha.booking.service;

import com.ektrepha.booking.dto.response.AttendanceMonthResponse;
import com.ektrepha.booking.dto.response.RegenerateOtpResponse;
import com.ektrepha.booking.dto.response.TodayAttendanceResponse;
import com.ektrepha.model.AttendanceOtpType;

/** PARENT-side reads/actions on their own booking's daily attendance codes (API Spec sheet rows 13-15). */
public interface BookingAttendanceService {

	TodayAttendanceResponse today(Long parentUserId, Long bookingId);

	RegenerateOtpResponse regenerateOtp(Long parentUserId, Long bookingId, AttendanceOtpType type);

	AttendanceMonthResponse month(Long parentUserId, Long bookingId, String month);

}
