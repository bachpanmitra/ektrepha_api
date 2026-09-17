package com.ektrepha.booking;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.ektrepha.model.Booking;
import com.ektrepha.model.BookingStatus;
import com.ektrepha.model.Nanny;
import com.ektrepha.model.NannyVerificationStatus;
import com.ektrepha.model.Parent;
import com.ektrepha.model.Review;
import com.ektrepha.model.ServiceType;
import com.ektrepha.model.User;
import com.ektrepha.model.UserSource;
import com.ektrepha.model.UserType;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.NannyRepository;
import com.ektrepha.repository.ParentRepository;
import com.ektrepha.repository.ReviewRepository;
import com.ektrepha.repository.ServiceTypeRepository;
import com.ektrepha.repository.UserRepository;

/**
 * HTTP-level coverage for B1/B2/H1/H2 (bookings, read side) — see
 * {@code docs/test-cases/bookings-read.md} for the full case list this mirrors/executes.
 */
@SpringBootTest
@Transactional
class BookingReadControllerApiTest {

	@Autowired
	private WebApplicationContext webApplicationContext;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private ParentRepository parentRepository;
	@Autowired
	private NannyRepository nannyRepository;
	@Autowired
	private ServiceTypeRepository serviceTypeRepository;
	@Autowired
	private BookingRepository bookingRepository;
	@Autowired
	private ReviewRepository reviewRepository;

	private MockMvc mockMvc;
	private ServiceType childcare;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.apply(SecurityMockMvcConfigurers.springSecurity())
				.build();
		childcare = serviceTypeRepository.findByCode("childcare").orElseThrow();
	}

	private Parent createParent() {
		User user = userRepository.save(User.builder()
				.name("Booking Test Parent").email("booking-test-" + System.nanoTime() + "@example.com")
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.EMAIL).userType(UserType.PARENT)
				.build());
		return parentRepository.save(Parent.builder().user(user).firstName("Test").build());
	}

	private Nanny createNanny() {
		User nannyUser = userRepository.save(User.builder()
				.name("Nanny").email("nanny-" + System.nanoTime() + "@example.com")
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.EMAIL).userType(UserType.NANNY)
				.build());
		return nannyRepository.save(Nanny.builder()
				.user(nannyUser).firstName("Priya").lastName("Sharma")
				.overallVerificationStatus(NannyVerificationStatus.VERIFIED)
				.build());
	}

	private Booking createBooking(Parent parent, Nanny nanny, BookingStatus status, Instant start, Instant end) {
		return bookingRepository.save(Booking.builder()
				.parent(parent).nanny(nanny).serviceType(childcare)
				.startTime(start).endTime(end).status(status)
				.totalAmount(new BigDecimal("1000.00"))
				.build());
	}

	// -------------------------------------------------------------- List — active

	@Test
	void listActive_returnsOnlyPendingConfirmedInProgress_sortedAscending() throws Exception {
		Parent parent = createParent();
		Nanny nanny = createNanny();
		Instant now = Instant.now();
		createBooking(parent, nanny, BookingStatus.CONFIRMED, now.plusSeconds(3 * 86400), now.plusSeconds(3 * 86400 + 3600));
		Booking inProgress = createBooking(parent, nanny, BookingStatus.IN_PROGRESS, now.minusSeconds(2400), now.plusSeconds(3600));
		createBooking(parent, nanny, BookingStatus.PENDING, now.plusSeconds(86400), now.plusSeconds(86400 + 3600));
		createBooking(parent, nanny, BookingStatus.COMPLETED, now.minusSeconds(86400), now.minusSeconds(80000));
		createBooking(parent, nanny, BookingStatus.CANCELLED, now.minusSeconds(90000), now.minusSeconds(86400));

		mockMvc.perform(get("/api/v1/bookings").param("scope", "active")
				.with(user(parent.getUser().getId().toString()).roles("PARENT")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements", is(3)))
				.andExpect(jsonPath("$.items[0].id", is(inProgress.getId().intValue())))
				.andExpect(jsonPath("$.items[0].elapsedSeconds", org.hamcrest.Matchers.greaterThan(0)));
	}

	@Test
	void listActive_withNoParentRowAtAll_returnsEmptyPageNot500() throws Exception {
		// Regression: originally 500'd for a user with no parent row.
		User user = userRepository.save(User.builder()
				.name("Fresh").email("fresh-booking-" + System.nanoTime() + "@example.com")
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.EMAIL).userType(UserType.PARENT)
				.build());

		mockMvc.perform(get("/api/v1/bookings").param("scope", "active")
				.with(user(user.getId().toString()).roles("PARENT")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements", is(0)));
	}

	@Test
	void list_missingScopeParam_isBadRequestNot500() throws Exception {
		// Regression: MissingServletRequestParameterException wasn't handled, fell through to a
		// generic 500 via the catch-all Exception handler.
		Parent parent = createParent();

		mockMvc.perform(get("/api/v1/bookings").with(user(parent.getUser().getId().toString()).roles("PARENT")))
				.andExpect(status().isBadRequest());
	}

	@Test
	void list_invalidScopeValue_isBadRequest() throws Exception {
		Parent parent = createParent();
		mockMvc.perform(get("/api/v1/bookings").param("scope", "bogus")
				.with(user(parent.getUser().getId().toString()).roles("PARENT")))
				.andExpect(status().isBadRequest());
	}

	// -------------------------------------------------------------- List — history

	@Test
	void listHistory_returnsOnlyCompletedCancelled_sortedDescending_withReviewPendingFlag() throws Exception {
		Parent parent = createParent();
		Nanny nanny = createNanny();
		Instant now = Instant.now();
		Booking older = createBooking(parent, nanny, BookingStatus.COMPLETED, now.minusSeconds(10 * 86400), now.minusSeconds(10 * 86400 - 3600));
		Booking newer = createBooking(parent, nanny, BookingStatus.COMPLETED, now.minusSeconds(2 * 86400), now.minusSeconds(2 * 86400 - 3600));
		reviewRepository.save(Review.builder().booking(newer).parent(parent).nanny(nanny).rating((short) 5).build());
		createBooking(parent, nanny, BookingStatus.PENDING, now.plusSeconds(86400), now.plusSeconds(90000));

		mockMvc.perform(get("/api/v1/bookings").param("scope", "history")
				.with(user(parent.getUser().getId().toString()).roles("PARENT")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements", is(2)))
				.andExpect(jsonPath("$.items[0].id", is(newer.getId().intValue())))
				.andExpect(jsonPath("$.items[0].reviewPending", is(false)))
				.andExpect(jsonPath("$.items[1].id", is(older.getId().intValue())))
				.andExpect(jsonPath("$.items[1].reviewPending", is(true)));
	}

	// -------------------------------------------------------------- Detail

	@Test
	void getDetail_completedBookingWithReview_includesReview() throws Exception {
		// Regression: this 500'd with a ClassCastException — ReviewRepository.findRatingAggregate
		// returning a bare Object[] got double-wrapped by Spring Data for a no-GROUP-BY aggregate
		// query. Fixed by returning List<Object[]>.
		Parent parent = createParent();
		Nanny nanny = createNanny();
		Instant now = Instant.now();
		Booking booking = createBooking(parent, nanny, BookingStatus.COMPLETED, now.minusSeconds(86400), now.minusSeconds(80000));
		reviewRepository.save(Review.builder().booking(booking).parent(parent).nanny(nanny)
				.rating((short) 5).comment("Great!").build());

		mockMvc.perform(get("/api/v1/bookings/" + booking.getId())
				.with(user(parent.getUser().getId().toString()).roles("PARENT")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.review.rating", is(5)))
				.andExpect(jsonPath("$.review.comment", is("Great!")));
	}

	@Test
	void getDetail_completedBookingWithoutReview_reviewIsNull() throws Exception {
		Parent parent = createParent();
		Nanny nanny = createNanny();
		Instant now = Instant.now();
		Booking booking = createBooking(parent, nanny, BookingStatus.COMPLETED, now.minusSeconds(86400), now.minusSeconds(80000));

		mockMvc.perform(get("/api/v1/bookings/" + booking.getId())
				.with(user(parent.getUser().getId().toString()).roles("PARENT")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.review", nullValue()));
	}

	@Test
	void getDetail_inProgressBooking_hasElapsedSeconds() throws Exception {
		Parent parent = createParent();
		Nanny nanny = createNanny();
		Instant now = Instant.now();
		Booking booking = createBooking(parent, nanny, BookingStatus.IN_PROGRESS, now.minusSeconds(1200), now.plusSeconds(3600));

		mockMvc.perform(get("/api/v1/bookings/" + booking.getId())
				.with(user(parent.getUser().getId().toString()).roles("PARENT")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.elapsedSeconds", org.hamcrest.Matchers.greaterThanOrEqualTo(1200)));
	}

	@Test
	void getDetail_belongingToAnotherParent_isNotFound() throws Exception {
		Parent owner = createParent();
		Nanny nanny = createNanny();
		Instant now = Instant.now();
		Booking booking = createBooking(owner, nanny, BookingStatus.CONFIRMED, now.plusSeconds(3600), now.plusSeconds(7200));
		Parent otherParent = createParent();

		mockMvc.perform(get("/api/v1/bookings/" + booking.getId())
				.with(user(otherParent.getUser().getId().toString()).roles("PARENT")))
				.andExpect(status().isNotFound());
	}

	@Test
	void getDetail_nonexistentId_isNotFound() throws Exception {
		Parent parent = createParent();
		mockMvc.perform(get("/api/v1/bookings/999999999")
				.with(user(parent.getUser().getId().toString()).roles("PARENT")))
				.andExpect(status().isNotFound());
	}

	// -------------------------------------------------------------- Cross-cutting

	@Test
	void list_withNannyRole_isForbidden() throws Exception {
		Parent parent = createParent();
		mockMvc.perform(get("/api/v1/bookings").param("scope", "active")
				.with(user(parent.getUser().getId().toString()).roles("NANNY")))
				.andExpect(status().isForbidden());
	}

	@Test
	void list_withNoAuth_isUnauthorized() throws Exception {
		mockMvc.perform(get("/api/v1/bookings").param("scope", "active"))
				.andExpect(status().isUnauthorized());
	}

}
