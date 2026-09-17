package com.ektrepha.nanny;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.assertj.core.api.Assertions;
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
import com.ektrepha.model.NannyVerification;
import com.ektrepha.model.NannyVerificationStatus;
import com.ektrepha.model.Parent;
import com.ektrepha.model.Review;
import com.ektrepha.model.ServiceType;
import com.ektrepha.model.User;
import com.ektrepha.model.UserSource;
import com.ektrepha.model.UserType;
import com.ektrepha.model.VerificationDocType;
import com.ektrepha.model.VerificationRecordStatus;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.NannyRepository;
import com.ektrepha.repository.NannyVerificationRepository;
import com.ektrepha.repository.ParentRepository;
import com.ektrepha.repository.ReviewRepository;
import com.ektrepha.repository.ServiceTypeRepository;
import com.ektrepha.repository.UserRepository;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * HTTP-level coverage for S1/S2 (nanny public profile, verification summary) and the nanny reviews
 * list — see {@code docs/test-cases/nanny-profile-and-verification.md} for the full case list this
 * mirrors/executes.
 */
@SpringBootTest
@Transactional
class NannyProfileControllerApiTest {

	@Autowired
	private WebApplicationContext webApplicationContext;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private ParentRepository parentRepository;
	@Autowired
	private NannyRepository nannyRepository;
	@Autowired
	private NannyVerificationRepository nannyVerificationRepository;
	@Autowired
	private ReviewRepository reviewRepository;
	@Autowired
	private BookingRepository bookingRepository;
	@Autowired
	private ServiceTypeRepository serviceTypeRepository;

	private MockMvc mockMvc;
	private String parentAuth;
	private Parent parent;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.apply(SecurityMockMvcConfigurers.springSecurity())
				.build();
		User parentUser = userRepository.save(User.builder()
				.name("Nanny Test Parent").email("nanny-view-" + System.nanoTime() + "@example.com")
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.EMAIL).userType(UserType.PARENT)
				.build());
		parent = parentRepository.save(Parent.builder().user(parentUser).firstName("Anjali").lastName("Mehta").build());
		parentAuth = parentUser.getId().toString();
	}

	private Nanny createNanny() {
		User nannyUser = userRepository.save(User.builder()
				.name("Nanny").email("nanny-" + System.nanoTime() + "@example.com")
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.EMAIL).userType(UserType.NANNY)
				.build());
		return nannyRepository.save(Nanny.builder()
				.user(nannyUser).firstName("Priya").lastName("Sharma")
				.bio("Loving, experienced caregiver.").yearsExperience(6).educationLevel("Graduate")
				.hourlyRate(new BigDecimal("450.00"))
				.overallVerificationStatus(NannyVerificationStatus.PENDING)
				.build());
	}

	private void verify(Nanny nanny, VerificationDocType type, VerificationRecordStatus status) {
		nannyVerificationRepository.save(NannyVerification.builder()
				.nanny(nanny).type(type).status(status)
				.s3Key("secret/s3/" + type + "/" + System.nanoTime())
				.vendorReferenceId("VENDOR-REF-SECRET-123")
				.rejectionReason("Confidential rejection detail")
				.reviewedAt(status == VerificationRecordStatus.VERIFIED ? Instant.now() : null)
				.build());
	}

	private void addReview(Nanny nanny, int rating, String comment) {
		ServiceType childcare = serviceTypeRepository.findByCode("childcare").orElseThrow();
		Booking booking = bookingRepository.save(Booking.builder()
				.parent(parent).nanny(nanny).serviceType(childcare)
				.startTime(Instant.now().minusSeconds(86400)).endTime(Instant.now().minusSeconds(80000))
				.status(BookingStatus.COMPLETED).totalAmount(new BigDecimal("1000.00"))
				.build());
		reviewRepository.save(Review.builder().booking(booking).parent(parent).nanny(nanny)
				.rating((short) rating).comment(comment).build());
	}

	// -------------------------------------------------------------- Profile (S1)

	@Test
	void getProfile_verifiedNanny_reportsVerifiedTrueAndRatingAggregate() throws Exception {
		Nanny nanny = createNanny();
		verify(nanny, VerificationDocType.ID_PROOF, VerificationRecordStatus.VERIFIED);
		verify(nanny, VerificationDocType.BACKGROUND_CHECK, VerificationRecordStatus.VERIFIED);
		verify(nanny, VerificationDocType.EDUCATION, VerificationRecordStatus.VERIFIED);
		nanny.applyRecomputedVerificationStatus(NannyVerificationStatus.VERIFIED);
		nannyRepository.save(nanny);
		addReview(nanny, 5, "Wonderful");
		addReview(nanny, 4, "Great");

		mockMvc.perform(get("/api/v1/nannies/" + nanny.getId()).with(user(parentAuth).roles("PARENT")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.verified", is(true)))
				.andExpect(jsonPath("$.ratingAvg", is(4.5)))
				.andExpect(jsonPath("$.reviewCount", is(2)))
				.andExpect(jsonPath("$.recentReviews[0].parentDisplayName", is("Anjali M.")));
	}

	@Test
	void getProfile_unverifiedNanny_reportsVerifiedFalse() throws Exception {
		Nanny nanny = createNanny(); // PENDING by default

		mockMvc.perform(get("/api/v1/nannies/" + nanny.getId()).with(user(parentAuth).roles("PARENT")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.verified", is(false)));
	}

	@Test
	void getProfile_nonexistentNanny_isNotFound() throws Exception {
		mockMvc.perform(get("/api/v1/nannies/999999999").with(user(parentAuth).roles("PARENT")))
				.andExpect(status().isNotFound());
	}

	// -------------------------------------------------------------- Verification (S2)

	@Test
	void getVerification_mixedStatuses_rollsUpCorrectlyAndCountsOnlyRequiredVerified() throws Exception {
		Nanny nanny = createNanny();
		verify(nanny, VerificationDocType.ID_PROOF, VerificationRecordStatus.VERIFIED);
		verify(nanny, VerificationDocType.BACKGROUND_CHECK, VerificationRecordStatus.VERIFIED);
		verify(nanny, VerificationDocType.EDUCATION, VerificationRecordStatus.VERIFIED);
		verify(nanny, VerificationDocType.FIRST_AID, VerificationRecordStatus.PENDING);
		verify(nanny, VerificationDocType.REFERENCE, VerificationRecordStatus.REJECTED);
		nanny.applyRecomputedVerificationStatus(NannyVerificationStatus.VERIFIED);
		nannyRepository.save(nanny);

		mockMvc.perform(get("/api/v1/nannies/" + nanny.getId() + "/verification").with(user(parentAuth).roles("PARENT")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.completedCount", is(3)))
				.andExpect(jsonPath("$.totalCount", is(5)))
				// REJECTED on REFERENCE (a non-required type) must not drag the rollup down from VERIFIED.
				.andExpect(jsonPath("$.overallStatus", is("VERIFIED")));
	}

	@Test
	void getVerification_neverLeaksSensitiveFields() throws Exception {
		Nanny nanny = createNanny();
		verify(nanny, VerificationDocType.ID_PROOF, VerificationRecordStatus.VERIFIED);

		String body = mockMvc.perform(get("/api/v1/nannies/" + nanny.getId() + "/verification").with(user(parentAuth).roles("PARENT")))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		Assertions.assertThat(body)
				.doesNotContain("s3Key")
				.doesNotContain("secret/s3/")
				.doesNotContain("vendorReferenceId")
				.doesNotContain("VENDOR-REF-SECRET-123")
				.doesNotContain("reviewedBy")
				.doesNotContain("rejectionReason")
				.doesNotContain("Confidential rejection detail");
	}

	@Test
	void getVerification_typeWithNoRecordAtAll_reportsPending() throws Exception {
		Nanny nanny = createNanny();
		verify(nanny, VerificationDocType.ID_PROOF, VerificationRecordStatus.VERIFIED);
		// BACKGROUND_CHECK, EDUCATION, FIRST_AID, REFERENCE: no rows at all for this nanny.

		mockMvc.perform(get("/api/v1/nannies/" + nanny.getId() + "/verification").with(user(parentAuth).roles("PARENT")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()", is(5)))
				.andExpect(jsonPath("$.items[?(@.type=='BACKGROUND_CHECK')].status", org.hamcrest.Matchers.contains("PENDING")))
				.andExpect(jsonPath("$.items[?(@.type=='BACKGROUND_CHECK')].verifiedAt", org.hamcrest.Matchers.contains((Object) null)));
	}

	@Test
	void getVerification_resubmissionAfterRejection_latestRecordWins() throws Exception {
		// createdAt is set unconditionally by @PrePersist (see NannyVerification.onCreate), so
		// ordering must come from real insert order + a small delay, not a manually-set timestamp
		// (same pattern as NannyVerificationRecomputeTest.createRecord).
		Nanny nanny = createNanny();
		nannyVerificationRepository.save(NannyVerification.builder()
				.nanny(nanny).type(VerificationDocType.ID_PROOF).status(VerificationRecordStatus.REJECTED)
				.s3Key("old").build());
		Thread.sleep(5);
		nannyVerificationRepository.save(NannyVerification.builder()
				.nanny(nanny).type(VerificationDocType.ID_PROOF).status(VerificationRecordStatus.VERIFIED)
				.s3Key("new").reviewedAt(Instant.now()).build());

		mockMvc.perform(get("/api/v1/nannies/" + nanny.getId() + "/verification").with(user(parentAuth).roles("PARENT")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[?(@.type=='ID_PROOF')].status", org.hamcrest.Matchers.contains("VERIFIED")));
	}

	// -------------------------------------------------------------- Reviews

	@Test
	void listReviews_masksReviewerIdentity() throws Exception {
		Nanny nanny = createNanny();
		addReview(nanny, 5, "Excellent");

		mockMvc.perform(get("/api/v1/nannies/" + nanny.getId() + "/reviews").with(user(parentAuth).roles("PARENT")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].parentDisplayName", is("Anjali M.")))
				.andExpect(jsonPath("$.totalElements", is(1)));
	}

	// -------------------------------------------------------------- Cross-cutting

	@Test
	void getProfile_withNannyRole_isForbidden() throws Exception {
		Nanny nanny = createNanny();
		mockMvc.perform(get("/api/v1/nannies/" + nanny.getId()).with(user(parentAuth).roles("NANNY")))
				.andExpect(status().isForbidden());
	}

	@Test
	void getProfile_withNoAuth_isUnauthorized() throws Exception {
		Nanny nanny = createNanny();
		mockMvc.perform(get("/api/v1/nannies/" + nanny.getId()))
				.andExpect(status().isUnauthorized());
	}

}
