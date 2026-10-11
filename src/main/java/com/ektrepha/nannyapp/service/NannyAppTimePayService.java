package com.ektrepha.nannyapp.service;

import java.time.YearMonth;
import java.util.List;

import com.ektrepha.nannyapp.dto.request.AttendanceCorrectionCreateRequest;
import com.ektrepha.nannyapp.dto.request.LeaveCreateRequest;
import com.ektrepha.nannyapp.dto.response.AttendanceSummaryResponse;
import com.ektrepha.nannyapp.dto.response.LeaveBalanceResponse;
import com.ektrepha.nannyapp.dto.response.LeaveRequestResponse;
import com.ektrepha.nannyapp.dto.response.PayslipResponse;
import com.ektrepha.nannyapp.dto.response.SalaryCurrentResponse;
import com.ektrepha.nannyapp.dto.response.UrlResponse;

/** Nanny staff app - attendance, leave and pay. */
public interface NannyAppTimePayService {

	AttendanceSummaryResponse attendance(Long userId, YearMonth month);

	void requestAttendanceCorrection(Long userId, AttendanceCorrectionCreateRequest request);

	LeaveBalanceResponse leaveBalance(Long userId);

	LeaveRequestResponse requestLeave(Long userId, LeaveCreateRequest request);

	SalaryCurrentResponse currentSalary(Long userId);

	List<PayslipResponse> payslips(Long userId);

	UrlResponse payslipDownloadUrl(Long userId, String payslipId);

}
