package com.ektrepha.booking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.booking.impl.AttendanceAlertJob;
import com.ektrepha.model.AddressLabel;
import com.ektrepha.model.AttendanceStatus;
import com.ektrepha.model.Booking;
import com.ektrepha.model.BookingAttendance;
import com.ektrepha.model.BookingStatus;
import com.ektrepha.model.Nanny;
import com.ektrepha.model.NannyVerificationStatus;
import com.ektrepha.model.Parent;
import com.ektrepha.model.ParentAddress;
import com.ektrepha.model.ServiceType;
import com.ektrepha.model.User;
import com.ektrepha.model.UserSource;
import com.ektrepha.model.UserType;
import com.ektrepha.repository.BookingAttendanceRepository;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.NannyRepository;
import com.ektrepha.repository.ParentAddressRepository;
import com.ektrepha.repository.ParentRepository;
import com.ektrepha.repository.ServiceTypeRepository;
import com.ektrepha.repository.UserRepository;

/** API Map rows 42-43 — late-check-in and forgot-checkout attendance alert sweeps. */
@SpringBootTest
@Transactional
class AttendanceAlertJobTest {

	private static final ZoneId INDIA_ZONE = ZoneId.of("Asia/Kolkata");

	@Autowired
	private AttendanceAlertJob job;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private ParentRepository parentRepository;
	@Autowired
	private ParentAddressRepository parentAddressRepository;
	@Autowired
	private NannyRepository nannyRepository;
	@Autowired
	private ServiceTypeRepository serviceTypeRepository;
	@Autowired
	private BookingRepository bookingRepository;
	@Autowired
	private BookingAttendanceRepository bookingAttendanceRepository;

	private Nanny createNanny() {
		User user = userRepository.save(User.builder()
				.name("Alert Job Nanny").phone("+9190066" + (System.nanoTime() % 100000))
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.PHONE).userType(UserType.NANNY)
				.build());
		return nannyRepository.save(Nanny.builder()
				.user(user).firstName("Alert").lastName("Nanny")
				.overallVerificationStatus(NannyVerificationStatus.APPROVED)
				.build());
	}

	private Booking createBooking(Nanny nanny, Instant start, Instant end) {
		User parentUser = userRepository.save(User.builder()
				.name("Alert Job Parent").email("alert-parent-" + System.nanoTime() + "@example.com")
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.EMAIL).userType(UserType.PARENT)
				.build());
		Parent parent = parentRepository.save(Parent.builder().user(parentUser).firstName("Test").build());
		ParentAddress address = parentAddressRepository.save(ParentAddress.builder()
				.parent(parent).label(AddressLabel.HOME).addressLine1("Test St")
				.pincode("560034").city("Bangalore").state("Karnataka").country("India").primary(true)
				.build());
		ServiceType childcare = serviceTypeRepository.findByCode("childcare").orElseThrow();
		return bookingRepository.save(Booking.builder()
				.parent(parent).nanny(nanny).serviceType(childcare).address(address)
				.startTime(start).endTime(end).status(BookingStatus.CONFIRMED)
				.build());
	}

	private BookingAttendance createAttendance(Booking booking, AttendanceStatus status) {
		return bookingAttendanceRepository.save(BookingAttendance.builder()
				.booking(booking).attendanceDate(LocalDate.now(INDIA_ZONE)).status(status)
				.build());
	}

	@Test
	void shiftStarted20MinAgo_notCheckedIn_getsLateAlert() {
		Nanny nanny = createNanny();
		Instant start = Instant.now().minusSeconds(20 * 60);
		Booking booking = createBooking(nanny, start, start.plusSeconds(4 * 3600));
		BookingAttendance attendance = createAttendance(booking, AttendanceStatus.PENDING);

		job.lateCheckInSweep();

		BookingAttendance reloaded = bookingAttendanceRepository.findById(attendance.getId()).orElseThrow();
		assertThat(reloaded.getLateCheckInAlertSentAt()).isNotNull();
	}

	@Test
	void shiftStarted5MinAgo_withinGrace_notAlertedYet() {
		Nanny nanny = createNanny();
		Instant start = Instant.now().minusSeconds(5 * 60);
		Booking booking = createBooking(nanny, start, start.plusSeconds(4 * 3600));
		BookingAttendance attendance = createAttendance(booking, AttendanceStatus.PENDING);

		job.lateCheckInSweep();

		BookingAttendance reloaded = bookingAttendanceRepository.findById(attendance.getId()).orElseThrow();
		assertThat(reloaded.getLateCheckInAlertSentAt()).isNull();
	}

	@Test
	void alreadyCheckedIn_isNeverFlaggedLate() {
		Nanny nanny = createNanny();
		Instant start = Instant.now().minusSeconds(20 * 60);
		Booking booking = createBooking(nanny, start, start.plusSeconds(4 * 3600));
		BookingAttendance attendance = createAttendance(booking, AttendanceStatus.CHECKED_IN);
		attendance.setCheckedInAt(Instant.now().minusSeconds(19 * 60));
		bookingAttendanceRepository.save(attendance);

		job.lateCheckInSweep();

		BookingAttendance reloaded = bookingAttendanceRepository.findById(attendance.getId()).orElseThrow();
		assertThat(reloaded.getLateCheckInAlertSentAt()).isNull();
	}

	@Test
	void shiftEnded70MinAgo_stillCheckedIn_getsForgotCheckOutAlert() {
		Nanny nanny = createNanny();
		Instant end = Instant.now().minusSeconds(70 * 60);
		Booking booking = createBooking(nanny, end.minusSeconds(4 * 3600), end);
		BookingAttendance attendance = createAttendance(booking, AttendanceStatus.CHECKED_IN);
		attendance.setCheckedInAt(end.minusSeconds(4 * 3600));
		bookingAttendanceRepository.save(attendance);

		job.forgotCheckOutSweep();

		BookingAttendance reloaded = bookingAttendanceRepository.findById(attendance.getId()).orElseThrow();
		assertThat(reloaded.getForgotCheckOutAlertSentAt()).isNotNull();
	}

	@Test
	void alreadyCheckedOut_isNeverFlagged() {
		Nanny nanny = createNanny();
		Instant end = Instant.now().minusSeconds(70 * 60);
		Booking booking = createBooking(nanny, end.minusSeconds(4 * 3600), end);
		BookingAttendance attendance = createAttendance(booking, AttendanceStatus.CHECKED_OUT);
		attendance.setCheckedInAt(end.minusSeconds(4 * 3600));
		attendance.setCheckedOutAt(end);
		bookingAttendanceRepository.save(attendance);

		job.forgotCheckOutSweep();

		BookingAttendance reloaded = bookingAttendanceRepository.findById(attendance.getId()).orElseThrow();
		assertThat(reloaded.getForgotCheckOutAlertSentAt()).isNull();
	}

}
