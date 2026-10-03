package com.ektrepha.nannysearch;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
import com.ektrepha.model.ServiceType;
import com.ektrepha.model.User;
import com.ektrepha.model.UserSource;
import com.ektrepha.model.UserType;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.NannyRepository;
import com.ektrepha.repository.ParentRepository;
import com.ektrepha.repository.ServiceTypeRepository;
import com.ektrepha.repository.UserRepository;

@SpringBootTest
@Transactional
class NannyReviewControllerApiTest {

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
				.name("Review Test Parent").email("review-test-" + System.nanoTime() + "@example.com")
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.EMAIL).userType(UserType.PARENT)
				.build());
		return parentRepository.save(Parent.builder().user(user).firstName("Test").build());
	}

	private Nanny createNanny() {
		User nannyUser = userRepository.save(User.builder()
				.name("Nanny").email("review-nanny-" + System.nanoTime() + "@example.com")
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.EMAIL).userType(UserType.NANNY)
				.build());
		return nannyRepository.save(Nanny.builder()
				.user(nannyUser).firstName("Priya").lastName("Sharma")
				.overallVerificationStatus(NannyVerificationStatus.APPROVED)
				.build());
	}

	private Booking createBooking(Parent parent, Nanny nanny, BookingStatus status) {
		Instant now = Instant.now();
		return bookingRepository.save(Booking.builder()
				.parent(parent).nanny(nanny).serviceType(childcare)
				.startTime(now.minusSeconds(7200)).endTime(now.minusSeconds(3600)).status(status)
				.totalAmount(new BigDecimal("1000.00"))
				.build());
	}

	@Test
	void submitReview_completedBookingWithNanny_isCreated() throws Exception {
		Parent parent = createParent();
		Nanny nanny = createNanny();
		Booking booking = createBooking(parent, nanny, BookingStatus.COMPLETED);

		mockMvc.perform(post("/api/v1/reviews").with(user(parent.getUser().getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"bookingId\":" + booking.getId() + ",\"rating\":5,\"comment\":\"Great work\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.bookingId", is(booking.getId().intValue())))
				.andExpect(jsonPath("$.rating", is(5)));
	}

	@Test
	void submitReview_completedBookingWithNoNannyAssigned_isBadRequestNot500() throws Exception {
		// Regression: an hourly-care pay-first booking that somehow reaches COMPLETED without ever
		// having a caregiver assigned (nanny=null) used to blow up with a NOT NULL constraint
		// violation on review.nanny_id (a raw 500) instead of a clean 400 - see
		// NannyReviewServiceImpl#resolveEligibleBooking.
		Parent parent = createParent();
		Booking booking = createBooking(parent, null, BookingStatus.COMPLETED);

		mockMvc.perform(post("/api/v1/reviews").with(user(parent.getUser().getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"bookingId\":" + booking.getId() + ",\"rating\":5,\"comment\":\"Great work\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void submitReview_notCompletedBooking_isBadRequest() throws Exception {
		Parent parent = createParent();
		Nanny nanny = createNanny();
		Booking booking = createBooking(parent, nanny, BookingStatus.CONFIRMED);

		mockMvc.perform(post("/api/v1/reviews").with(user(parent.getUser().getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"bookingId\":" + booking.getId() + ",\"rating\":5,\"comment\":\"Great work\"}"))
				.andExpect(status().isBadRequest());
	}

}
