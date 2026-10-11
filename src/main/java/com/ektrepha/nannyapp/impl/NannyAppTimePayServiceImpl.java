package com.ektrepha.nannyapp.impl;

import static com.ektrepha.nannyapp.impl.NannyAppSupport.istDate;
import static com.ektrepha.nannyapp.impl.NannyAppSupport.startOfDay;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.exception.NannyAppResourceNotFoundException;
import com.ektrepha.exception.NannyAppRuleViolationException;
import com.ektrepha.model.AttendanceCorrectionRequest;
import com.ektrepha.model.Booking;
import com.ektrepha.model.BookingStatus;
import com.ektrepha.model.LeaveRequest;
import com.ektrepha.model.Nanny;
import com.ektrepha.model.NannyEmployment;
import com.ektrepha.model.NannyPayslip;
import com.ektrepha.model.RequestStatus;
import com.ektrepha.nannyapp.dto.request.AttendanceCorrectionCreateRequest;
import com.ektrepha.nannyapp.dto.request.LeaveCreateRequest;
import com.ektrepha.nannyapp.dto.response.AttendanceDayResponse;
import com.ektrepha.nannyapp.dto.response.AttendanceSummaryResponse;
import com.ektrepha.nannyapp.dto.response.LeaveBalanceResponse;
import com.ektrepha.nannyapp.dto.response.LeaveRequestResponse;
import com.ektrepha.nannyapp.dto.response.PayslipResponse;
import com.ektrepha.nannyapp.dto.response.SalaryCurrentResponse;
import com.ektrepha.nannyapp.dto.response.UrlResponse;
import com.ektrepha.nannyapp.service.NannyAppTimePayService;
import com.ektrepha.repository.AttendanceCorrectionRequestRepository;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.LeaveRequestRepository;
import com.ektrepha.repository.NannyPayslipRepository;
import com.ektrepha.storage.S3PhotoUrlService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NannyAppTimePayServiceImpl implements NannyAppTimePayService {

	private static final List<BookingStatus> WORKED_STATUSES = List.of(BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS,
			BookingStatus.COMPLETED);
	/** 26 working days x 8 hours - the divisor that turns a monthly salary into an hourly overtime rate. */
	private static final double WORKING_HOURS_PER_MONTH = 208;
	private static final BigDecimal PF_WAGE_CEILING = BigDecimal.valueOf(15_000);
	private static final BigDecimal ESI_WAGE_CEILING = BigDecimal.valueOf(21_000);

	private final NannyAppSupport support;
	private final BookingRepository bookingRepository;
	private final LeaveRequestRepository leaveRequestRepository;
	private final AttendanceCorrectionRequestRepository attendanceCorrectionRequestRepository;
	private final NannyPayslipRepository nannyPayslipRepository;
	private final S3PhotoUrlService s3PhotoUrlService;

	@Value("${app.nanny-app.leave.casual-days-per-year:12}")
	private int casualDaysPerYear;
	@Value("${app.nanny-app.leave.sick-days-per-year:6}")
	private int sickDaysPerYear;
	@Value("${app.nanny-app.leave.earned-days-per-year:15}")
	private int earnedDaysPerYear;

	@Override
	@Transactional(readOnly = true)
	public AttendanceSummaryResponse attendance(Long userId, YearMonth month) {
		Nanny nanny = support.nanny(userId);
		LocalDate first = month.atDay(1);
		LocalDate last = month.atEndOfMonth();
		LocalDate today = NannyAppSupport.today();
		Map<LocalDate, List<Booking>> byDay = monthBookings(nanny, month).stream()
				.collect(Collectors.groupingBy(b -> istDate(b.getStartTime())));
		List<LeaveRequest> leave = leaveRequestRepository.findForNannyOverlapping(nanny.getId(), RequestStatus.APPROVED, first, last);

		List<AttendanceDayResponse> days = new ArrayList<>();
		int present = 0;
		int late = 0;
		int onLeave = 0;
		for (LocalDate day = first; !day.isAfter(last); day = day.plusDays(1)) {
			String status = dayStatus(day, today, byDay.getOrDefault(day, List.of()), leave);
			switch (status) {
				case "PRESENT" -> present++;
				case "LATE" -> late++;
				case "LEAVE" -> onLeave++;
				default -> { }
			}
			days.add(new AttendanceDayResponse(day.toString(), status));
		}
		return new AttendanceSummaryResponse(month.toString(), present, late, onLeave, overtimeHours(byDay.values().stream()
				.flatMap(List::stream).toList()), days);
	}

	@Override
	@Transactional
	public void requestAttendanceCorrection(Long userId, AttendanceCorrectionCreateRequest request) {
		Nanny nanny = support.nanny(userId);
		// The correction is filed against that day's shift - the admin Approvals screen reviews it per booking.
		Booking booking = bookingRepository.findByNannyIdStartingBetween(nanny.getId(), WORKED_STATUSES,
				startOfDay(request.date()), startOfDay(request.date().plusDays(1))).stream().findFirst()
				.orElseThrow(() -> new NannyAppResourceNotFoundException("You had no shift on " + request.date()));
		attendanceCorrectionRequestRepository.save(AttendanceCorrectionRequest.builder()
				.booking(booking).nanny(nanny).details(request.note().trim()).build());
	}

	@Override
	@Transactional(readOnly = true)
	public LeaveBalanceResponse leaveBalance(Long userId) {
		Nanny nanny = support.nanny(userId);
		Map<String, Long> used = usedLeaveDaysThisYear(nanny);
		return new LeaveBalanceResponse(
				left(casualDaysPerYear, used.get("CASUAL")),
				left(sickDaysPerYear, used.get("SICK")),
				left(earnedDaysPerYear, used.get("EARNED")));
	}

	@Override
	@Transactional
	public LeaveRequestResponse requestLeave(Long userId, LeaveCreateRequest request) {
		Nanny nanny = support.nanny(userId);
		if (request.toDate().isBefore(request.fromDate())) {
			throw new IllegalArgumentException("toDate must be on or after fromDate");
		}
		if (request.fromDate().isBefore(NannyAppSupport.today())) {
			throw new IllegalArgumentException("Leave can't start in the past");
		}
		long days = ChronoUnit.DAYS.between(request.fromDate(), request.toDate()) + 1;
		int allowance = switch (request.type()) {
			case "SICK" -> sickDaysPerYear;
			case "EARNED" -> earnedDaysPerYear;
			default -> casualDaysPerYear;
		};
		long left = left(allowance, usedLeaveDaysThisYear(nanny).get(request.type()));
		if (days > left) {
			throw new NannyAppRuleViolationException("Only " + left + " " + request.type().toLowerCase() + " leave days left");
		}
		int shifts = bookingRepository.findByNannyIdStartingBetween(nanny.getId(), List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED),
				startOfDay(request.fromDate()), startOfDay(request.toDate().plusDays(1))).size();
		LeaveRequest saved = leaveRequestRepository.save(LeaveRequest.builder()
				.nanny(nanny).startDate(request.fromDate()).endDate(request.toDate())
				.leaveType(request.type()).reason(request.type().charAt(0) + request.type().substring(1).toLowerCase() + " leave")
				.build());
		return new LeaveRequestResponse(String.valueOf(saved.getId()), saved.getLeaveType(), saved.getStartDate(), saved.getEndDate(),
				days, shifts, NannyAppShiftServiceImpl.requestStatus(saved.getStatus()));
	}

	@Override
	@Transactional(readOnly = true)
	public SalaryCurrentResponse currentSalary(Long userId) {
		Nanny nanny = support.nanny(userId);
		NannyEmployment employment = support.employment(nanny);
		YearMonth month = YearMonth.from(NannyAppSupport.today());
		BigDecimal base = employment.getBaseMonthlySalary() == null ? BigDecimal.ZERO : employment.getBaseMonthlySalary();
		double overtimeHours = overtimeHours(monthBookings(nanny, month));
		long overtimeAmount = Math.round(overtimeHours * base.doubleValue() / WORKING_HOURS_PER_MONTH);
		long deduction = statutoryDeduction(base);
		long baseRupees = base.setScale(0, RoundingMode.HALF_UP).longValue();
		long bonus = 0;
		return new SalaryCurrentResponse(month.toString(), Math.max(0, baseRupees + overtimeAmount + bonus - deduction),
				month.plusMonths(1).atDay(1), baseRupees, overtimeAmount, overtimeHours, bonus, deduction,
				NannyAppSupport.maskAccount(employment.getBankAccountNumber()));
	}

	@Override
	@Transactional(readOnly = true)
	public List<PayslipResponse> payslips(Long userId) {
		Nanny nanny = support.nanny(userId);
		return nannyPayslipRepository.findByNannyIdOrderByMonthDesc(nanny.getId()).stream()
				.map(p -> new PayslipResponse(String.valueOf(p.getId()), p.getMonth(),
						p.getAmount().setScale(0, RoundingMode.HALF_UP).longValue(), p.getPaidDate()))
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public UrlResponse payslipDownloadUrl(Long userId, String payslipId) {
		Nanny nanny = support.nanny(userId);
		NannyPayslip payslip = nannyPayslipRepository.findByIdAndNannyId(NannyAppSupport.parseId(payslipId), nanny.getId())
				.orElseThrow(() -> new NannyAppResourceNotFoundException("No payslip with id " + payslipId));
		String url = s3PhotoUrlService.presign(payslip.getS3Key());
		if (url == null) {
			throw new NannyAppResourceNotFoundException("This payslip's PDF isn't available yet");
		}
		return new UrlResponse(url);
	}

	private List<Booking> monthBookings(Nanny nanny, YearMonth month) {
		return bookingRepository.findByNannyIdStartingBetween(nanny.getId(), WORKED_STATUSES,
				startOfDay(month.atDay(1)), startOfDay(month.plusMonths(1).atDay(1)));
	}

	private static String dayStatus(LocalDate day, LocalDate today, List<Booking> bookings, List<LeaveRequest> leave) {
		List<Booking> attended = bookings.stream().filter(b -> b.getCheckedInAt() != null).toList();
		if (!attended.isEmpty()) {
			return attended.stream().allMatch(NannyAppProfileServiceImpl::checkedInOnTime) ? "PRESENT" : "LATE";
		}
		if (leave.stream().anyMatch(l -> !day.isBefore(l.getStartDate()) && !day.isAfter(l.getEndDate()))) {
			return "LEAVE";
		}
		if (day.isAfter(today)) {
			return "FUTURE";
		}
		return day.getDayOfWeek() == DayOfWeek.SUNDAY && bookings.isEmpty() ? "WEEKLY_OFF" : "NONE";
	}

	/** Time worked past each booking's scheduled end, in hours (1 decimal). */
	private static double overtimeHours(List<Booking> bookings) {
		long minutes = bookings.stream()
				.filter(b -> b.getCheckedOutAt() != null && b.getCheckedOutAt().isAfter(b.getEndTime()))
				.mapToLong(b -> Duration.between(b.getEndTime(), b.getCheckedOutAt()).toMinutes())
				.sum();
		return Math.round(minutes / 6.0) / 10.0;
	}

	/** Employee PF (12% of wages up to the 15k ceiling) plus ESI (0.75%, only when wages are within the 21k ceiling). */
	static long statutoryDeduction(BigDecimal base) {
		if (base.signum() <= 0) {
			return 0;
		}
		double pf = base.min(PF_WAGE_CEILING).doubleValue() * 0.12;
		double esi = base.compareTo(ESI_WAGE_CEILING) <= 0 ? base.doubleValue() * 0.0075 : 0;
		return Math.round(pf + esi);
	}

	/** Pending and approved leave both count against the balance, so a nanny can't over-request while HR reviews. */
	private Map<String, Long> usedLeaveDaysThisYear(Nanny nanny) {
		LocalDate today = NannyAppSupport.today();
		LocalDate yearStart = today.withDayOfYear(1);
		LocalDate yearEnd = yearStart.plusYears(1).minusDays(1);
		List<LeaveRequest> requests = new ArrayList<>();
		requests.addAll(leaveRequestRepository.findForNannyOverlapping(nanny.getId(), RequestStatus.APPROVED, yearStart, yearEnd));
		requests.addAll(leaveRequestRepository.findForNannyOverlapping(nanny.getId(), RequestStatus.PENDING, yearStart, yearEnd));
		return requests.stream()
				.sorted(Comparator.comparing(LeaveRequest::getStartDate))
				.collect(Collectors.groupingBy(l -> l.getLeaveType() == null ? "CASUAL" : l.getLeaveType(),
						Collectors.summingLong(l -> ChronoUnit.DAYS.between(
								l.getStartDate().isBefore(yearStart) ? yearStart : l.getStartDate(),
								(l.getEndDate().isAfter(yearEnd) ? yearEnd : l.getEndDate()).plusDays(1)))));
	}

	private static int left(int allowance, Long used) {
		return (int) Math.max(0, allowance - (used == null ? 0 : used));
	}

}
