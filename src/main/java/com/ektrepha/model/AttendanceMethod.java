package com.ektrepha.model;

/** {@code booking_attendance.check_in_method} / {@code check_out_method} - OTP from the nanny app, or MANUAL by an ops user (see {@code AttendanceCorrectionRequest} for the separate nanny-initiated correction request). */
public enum AttendanceMethod {

	OTP,
	MANUAL

}
