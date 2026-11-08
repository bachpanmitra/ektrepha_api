package com.ektrepha.booking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

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
import com.ektrepha.booking.impl.TripReminderJob;
import com.ektrepha.repository.BookingAttendanceRepository;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.NannyRepository;
import com.ektrepha.repository.ParentAddressRepository;
import com.ektrepha.repository.ParentRepository;
import com.ektrepha.repository.ServiceTypeRepository;
import com.ektrepha.repository.UserRepository;

/** API Map row 64 — the same-day ~15-min-before-start push reminder. */
@SpringBootTest
@Transactional
class TripReminderJobTest {

	private static final ZoneId INDIA_ZONE = ZoneId.of("Asia/Kolkata");

	@Autowired
	private TripReminderJob job;
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
				.name("Reminder Nanny").phone("+9190099" + (System.nanoTime() % 100000))
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.PHONE).userType(UserType.NANNY)
				.build());
		return nannyRepository.save(Nanny.builder()
				.user(user).firstName("Reminder").lastName("Nanny")
				.overallVerificationStatus(NannyVerificationStatus.APPROVED)
				.build());
	}

	private Booking createBooking(Nanny nanny, Instant start) {
		User parentUser = userRepository.save(User.builder()
				.name("Reminder Parent").email("reminder-parent-" + System.nanoTime() + "@example.com")
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
				.startTime(start).endTime(start.plusSeconds(4 * 3600)).status(BookingStatus.CONFIRMED)
				.build());
	}

	private BookingAttendance createPendingAttendance(Booking booking) {
		return bookingAttendanceRepository.save(BookingAttendance.builder()
				.booking(booking).attendanceDate(LocalDate.now(INDIA_ZONE)).status(AttendanceStatus.PENDING)
				.build());
	}

	@Test
	void shiftStarts15MinFromNow_getsReminded() {
		Nanny nanny = createNanny();
		Booking booking = createBooking(nanny, Instant.now().plusSeconds(15 * 60));
		BookingAttendance attendance = createPendingAttendance(booking);

		job.sendDueReminders();

		BookingAttendance reloaded = bookingAttendanceRepository.findById(attendance.getId()).orElseThrow();
		assertThat(reloaded.getReminderSentAt()).isNotNull();
	}

	@Test
	void shiftStarts5MinFromNow_outsideWindow_notRemindedYet() {
		Nanny nanny = createNanny();
		Booking booking = createBooking(nanny, Instant.now().plusSeconds(5 * 60));
		BookingAttendance attendance = createPendingAttendance(booking);

		job.sendDueReminders();

		BookingAttendance reloaded = bookingAttendanceRepository.findById(attendance.getId()).orElseThrow();
		assertThat(reloaded.getReminderSentAt()).isNull();
	}

	@Test
	void alreadyReminded_isNotRemindedAgain() {
		Nanny nanny = createNanny();
		Booking booking = createBooking(nanny, Instant.now().plusSeconds(15 * 60));
		BookingAttendance attendance = createPendingAttendance(booking);
		attendance.setReminderSentAt(Instant.now().minusSeconds(60));
		bookingAttendanceRepository.save(attendance);

		job.sendDueReminders();

		BookingAttendance reloaded = bookingAttendanceRepository.findById(attendance.getId()).orElseThrow();
		assertThat(reloaded.getReminderSentAt()).isBefore(Instant.now().minusSeconds(30));
	}

}
