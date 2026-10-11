package com.ektrepha.nannyapp.controller;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.nannyapp.dto.request.AbsenceCreateRequest;
import com.ektrepha.nannyapp.dto.request.AttendanceCorrectionCreateRequest;
import com.ektrepha.nannyapp.dto.request.BankAccountRequest;
import com.ektrepha.nannyapp.dto.request.ChangeRequestCreateRequest;
import com.ektrepha.nannyapp.dto.request.EmergencyContactRequest;
import com.ektrepha.nannyapp.dto.request.ExtensionRequest;
import com.ektrepha.nannyapp.dto.request.FamilyReviewRequest;
import com.ektrepha.nannyapp.dto.request.IncidentCreateRequest;
import com.ektrepha.nannyapp.dto.request.LeaveCreateRequest;
import com.ektrepha.nannyapp.dto.request.ShiftLocationRequest;
import com.ektrepha.nannyapp.dto.request.ShiftUpdateRequest;
import com.ektrepha.nannyapp.dto.request.SosCreateRequest;
import com.ektrepha.nannyapp.dto.request.UpdateMeRequest;
import com.ektrepha.nannyapp.dto.response.AbsenceResponse;
import com.ektrepha.nannyapp.dto.response.AttendanceSummaryResponse;
import com.ektrepha.nannyapp.dto.response.BankAccountResponse;
import com.ektrepha.nannyapp.dto.response.ChangeRequestResponse;
import com.ektrepha.nannyapp.dto.response.DocumentResponse;
import com.ektrepha.nannyapp.dto.response.EmergencyContactResponse;
import com.ektrepha.nannyapp.dto.response.IncidentResponse;
import com.ektrepha.nannyapp.dto.response.LeaveBalanceResponse;
import com.ektrepha.nannyapp.dto.response.LeaveRequestResponse;
import com.ektrepha.nannyapp.dto.response.NannyMeResponse;
import com.ektrepha.nannyapp.dto.response.OnboardingResponse;
import com.ektrepha.nannyapp.dto.response.PayslipResponse;
import com.ektrepha.nannyapp.dto.response.SalaryCurrentResponse;
import com.ektrepha.nannyapp.dto.response.ShiftDetailResponse;
import com.ektrepha.nannyapp.dto.response.ShiftExtensionResponse;
import com.ektrepha.nannyapp.dto.response.ShiftSummaryResponse;
import com.ektrepha.nannyapp.dto.response.ShiftUpdateResponse;
import com.ektrepha.nannyapp.dto.response.SosEventResponse;
import com.ektrepha.nannyapp.dto.response.StatutoryResponse;
import com.ektrepha.nannyapp.dto.response.TrainingResponse;
import com.ektrepha.nannyapp.dto.response.UrlResponse;
import com.ektrepha.nannyapp.service.NannyAppProfileService;
import com.ektrepha.nannyapp.service.NannyAppSafetyService;
import com.ektrepha.nannyapp.service.NannyAppShiftService;
import com.ektrepha.nannyapp.service.NannyAppTimePayService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Every endpoint the nanny staff app (ektrepha-nanny-ui) calls, under one NANNY-only prefix
 * (see SecurityConfig). Response shapes match that app's api/types.ts one-for-one; its
 * docs/API_STATUS.md is the written contract.
 */
@RestController
@RequestMapping("/api/v1/nanny/me")
@RequiredArgsConstructor
public class NannyAppController {

	private final NannyAppProfileService profileService;
	private final NannyAppShiftService shiftService;
	private final NannyAppTimePayService timePayService;
	private final NannyAppSafetyService safetyService;

	// ------------------------------------------------------------------------------- me & joining

	@GetMapping
	public NannyMeResponse me(Authentication auth) {
		return profileService.me(userId(auth));
	}

	@PatchMapping
	public NannyMeResponse updateMe(Authentication auth, @Valid @RequestBody UpdateMeRequest request) {
		return profileService.updateMe(userId(auth), request);
	}

	@GetMapping("/onboarding")
	public OnboardingResponse onboarding(Authentication auth) {
		return profileService.onboarding(userId(auth));
	}

	@GetMapping("/documents")
	public List<DocumentResponse> documents(Authentication auth) {
		return profileService.documents(userId(auth));
	}

	@GetMapping("/bank-account")
	public BankAccountResponse bankAccount(Authentication auth) {
		return profileService.bankAccount(userId(auth));
	}

	@PutMapping("/bank-account")
	public BankAccountResponse saveBankAccount(Authentication auth, @Valid @RequestBody BankAccountRequest request) {
		return profileService.saveBankAccount(userId(auth), request);
	}

	@GetMapping("/statutory")
	public StatutoryResponse statutory(Authentication auth) {
		return profileService.statutory(userId(auth));
	}

	@GetMapping("/trainings")
	public List<TrainingResponse> trainings(Authentication auth) {
		return profileService.trainings(userId(auth));
	}

	@GetMapping("/emergency-contact")
	public EmergencyContactResponse emergencyContact(Authentication auth) {
		return profileService.emergencyContact(userId(auth));
	}

	@PutMapping("/emergency-contact")
	public EmergencyContactResponse saveEmergencyContact(Authentication auth, @Valid @RequestBody EmergencyContactRequest request) {
		return profileService.saveEmergencyContact(userId(auth), request);
	}

	// ------------------------------------------------------------------------------------ shifts

	/** 200 with an empty body when nothing is scheduled today. */
	@GetMapping("/today")
	public ResponseEntity<ShiftSummaryResponse> today(Authentication auth) {
		return ResponseEntity.ok(shiftService.today(userId(auth)));
	}

	@GetMapping("/roster")
	public List<ShiftSummaryResponse> roster(Authentication auth,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
		return shiftService.roster(userId(auth), weekStart);
	}

	@GetMapping("/shifts/{id}")
	public ShiftDetailResponse shift(Authentication auth, @PathVariable String id) {
		return shiftService.shift(userId(auth), id);
	}

	@PostMapping("/shifts/{id}/on-the-way")
	public ResponseEntity<Void> onTheWay(Authentication auth, @PathVariable String id) {
		shiftService.markOnTheWay(userId(auth), id);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/shifts/{id}/check-in")
	public ShiftDetailResponse checkIn(Authentication auth, @PathVariable String id, @RequestBody(required = false) ShiftLocationRequest request) {
		return shiftService.checkIn(userId(auth), id, request);
	}

	@PostMapping("/shifts/{id}/check-out")
	public ShiftDetailResponse checkOut(Authentication auth, @PathVariable String id, @RequestBody(required = false) ShiftLocationRequest request) {
		return shiftService.checkOut(userId(auth), id, request);
	}

	@PostMapping("/shifts/{id}/extensions")
	public ResponseEntity<ShiftExtensionResponse> extension(Authentication auth, @PathVariable String id, @Valid @RequestBody ExtensionRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(shiftService.requestExtension(userId(auth), id, request.minutes()));
	}

	@PostMapping("/shifts/{id}/updates")
	public ResponseEntity<ShiftUpdateResponse> update(Authentication auth, @PathVariable String id, @Valid @RequestBody ShiftUpdateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(shiftService.addUpdate(userId(auth), id, request.text()));
	}

	@PostMapping("/shifts/{id}/family-review")
	public ResponseEntity<Void> familyReview(Authentication auth, @PathVariable String id, @Valid @RequestBody FamilyReviewRequest request) {
		shiftService.submitFamilyReview(userId(auth), id, request);
		return ResponseEntity.status(HttpStatus.CREATED).build();
	}

	@PostMapping("/shifts/{id}/change-requests")
	public ResponseEntity<ChangeRequestResponse> changeRequest(Authentication auth, @PathVariable String id,
			@Valid @RequestBody ChangeRequestCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(shiftService.requestChange(userId(auth), id, request));
	}

	@PostMapping("/absences")
	public ResponseEntity<AbsenceResponse> absence(Authentication auth, @Valid @RequestBody AbsenceCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(shiftService.reportAbsence(userId(auth), request));
	}

	// ---------------------------------------------------------------------------------- time & pay

	@GetMapping("/attendance")
	public AttendanceSummaryResponse attendance(Authentication auth, @RequestParam String month) {
		YearMonth parsed;
		try {
			parsed = YearMonth.parse(month);
		} catch (DateTimeParseException e) {
			throw new IllegalArgumentException("month must be YYYY-MM");
		}
		return timePayService.attendance(userId(auth), parsed);
	}

	@PostMapping("/attendance/corrections")
	public ResponseEntity<Void> attendanceCorrection(Authentication auth, @Valid @RequestBody AttendanceCorrectionCreateRequest request) {
		timePayService.requestAttendanceCorrection(userId(auth), request);
		return ResponseEntity.status(HttpStatus.CREATED).build();
	}

	@GetMapping("/leave/balance")
	public LeaveBalanceResponse leaveBalance(Authentication auth) {
		return timePayService.leaveBalance(userId(auth));
	}

	@PostMapping("/leave-requests")
	public ResponseEntity<LeaveRequestResponse> leaveRequest(Authentication auth, @Valid @RequestBody LeaveCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(timePayService.requestLeave(userId(auth), request));
	}

	@GetMapping("/salary/current")
	public SalaryCurrentResponse salary(Authentication auth) {
		return timePayService.currentSalary(userId(auth));
	}

	@GetMapping("/payslips")
	public List<PayslipResponse> payslips(Authentication auth) {
		return timePayService.payslips(userId(auth));
	}

	@GetMapping("/payslips/{id}/download")
	public UrlResponse payslipDownload(Authentication auth, @PathVariable String id) {
		return timePayService.payslipDownloadUrl(userId(auth), id);
	}

	// -------------------------------------------------------------------------------------- safety

	@PostMapping("/sos")
	public ResponseEntity<SosEventResponse> sos(Authentication auth, @RequestBody(required = false) SosCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(safetyService.raiseSos(userId(auth), request));
	}

	@PostMapping("/sos/{id}/cancel")
	public SosEventResponse cancelSos(Authentication auth, @PathVariable String id) {
		return safetyService.cancelSos(userId(auth), id);
	}

	@PostMapping("/incidents")
	public ResponseEntity<IncidentResponse> incident(Authentication auth, @Valid @RequestBody IncidentCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(safetyService.reportIncident(userId(auth), request));
	}

	private Long userId(Authentication authentication) {
		return Long.valueOf(authentication.getName());
	}

}
