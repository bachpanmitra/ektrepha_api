package com.ektrepha.admin;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
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

/** HTTP-level coverage for the admin "Nannies" endpoints (Phase 4): list/detail/create/update/documents/roster/reviews. */
@SpringBootTest
@Transactional
class AdminNannyControllerApiTest {

	@Autowired
	private WebApplicationContext webApplicationContext;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private NannyRepository nannyRepository;
	@Autowired
	private ParentRepository parentRepository;
	@Autowired
	private BookingRepository bookingRepository;
	@Autowired
	private ReviewRepository reviewRepository;
	@Autowired
	private ServiceTypeRepository serviceTypeRepository;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.apply(SecurityMockMvcConfigurers.springSecurity())
				.build();
	}

	private Nanny createNanny(String firstName, String phone) {
		User user = userRepository.save(User.builder()
				.name(firstName).phone(phone).email(firstName.toLowerCase() + System.nanoTime() + "@example.com")
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.PHONE).userType(UserType.NANNY)
				.build());
		return nannyRepository.save(Nanny.builder()
				.user(user).firstName(firstName).lastName("T")
				.overallVerificationStatus(NannyVerificationStatus.PENDING)
				.build());
	}

	@Test
	void create_validRequest_createsNannyAccount() throws Exception {
		String phone = "+9190000" + (System.nanoTime() % 100000);
		mockMvc.perform(post("/api/v1/admin/nannies").with(user("999").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"firstName\":\"Priya\",\"lastName\":\"Sharma\",\"phone\":\"" + phone + "\",\"dob\":\"1995-05-05\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.firstName", is("Priya")))
				.andExpect(jsonPath("$.phone", is(phone)))
				.andExpect(jsonPath("$.verificationStatus", is("PENDING")))
				.andExpect(jsonPath("$.active", is(true)));
	}

	@Test
	void create_duplicatePhone_isConflict() throws Exception {
		String phone = "+9190001" + (System.nanoTime() % 100000);
		createNanny("Existing", phone);

		mockMvc.perform(post("/api/v1/admin/nannies").with(user("999").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"firstName\":\"Another\",\"phone\":\"" + phone + "\",\"dob\":\"1995-05-05\"}"))
				.andExpect(status().isConflict());
	}

	@Test
	void list_filtersByQ_matchesName() throws Exception {
		String uniqueName = "Zelda" + System.nanoTime();
		Nanny nanny = createNanny(uniqueName, "+9190002" + (System.nanoTime() % 100000));

		mockMvc.perform(get("/api/v1/admin/nannies").param("q", uniqueName).with(user("999").roles("ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[?(@.id == " + nanny.getId() + ")]", hasSize(1)));
	}

	@Test
	void detail_returnsProfileWithEmptyZoneMappings() throws Exception {
		Nanny nanny = createNanny("Detail Nanny", "+9190003" + (System.nanoTime() % 100000));

		mockMvc.perform(get("/api/v1/admin/nannies/" + nanny.getId()).with(user("999").roles("ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.firstName", is("Detail Nanny")))
				.andExpect(jsonPath("$.zoneMappings", hasSize(0)))
				.andExpect(jsonPath("$.reviewCount", is(0)));
	}

	@Test
	void detail_unknownId_isNotFound() throws Exception {
		mockMvc.perform(get("/api/v1/admin/nannies/999999999").with(user("999").roles("ADMIN")))
				.andExpect(status().isNotFound());
	}

	@Test
	void update_setsInactive() throws Exception {
		Nanny nanny = createNanny("Active Nanny", "+9190004" + (System.nanoTime() % 100000));

		mockMvc.perform(patch("/api/v1/admin/nannies/" + nanny.getId()).with(user("999").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"active\":false}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active", is(false)));
	}

	@Test
	void documents_emptyForNewNanny() throws Exception {
		Nanny nanny = createNanny("Doc Nanny", "+9190005" + (System.nanoTime() % 100000));

		mockMvc.perform(get("/api/v1/admin/nannies/" + nanny.getId() + "/documents").with(user("999").roles("ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void uploadDocument_asAdmin_createsPendingRecord() throws Exception {
		Nanny nanny = createNanny("Upload Nanny", "+9190008" + (System.nanoTime() % 100000));
		MockMultipartFile file = new MockMultipartFile("file", "id-proof.jpg", "image/jpeg", "fake-bytes".getBytes());

		mockMvc.perform(multipart("/api/v1/admin/nannies/" + nanny.getId() + "/documents")
				.file(file)
				.param("type", "ID_PROOF")
				.with(user("999").roles("ADMIN")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.type", is("ID_PROOF")))
				.andExpect(jsonPath("$.status", is("PENDING")));

		mockMvc.perform(get("/api/v1/admin/nannies/" + nanny.getId() + "/documents").with(user("999").roles("ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)));
	}

	@Test
	void uploadDocument_unknownNanny_isNotFound() throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "id-proof.jpg", "image/jpeg", "fake-bytes".getBytes());

		mockMvc.perform(multipart("/api/v1/admin/nannies/999999999/documents")
				.file(file)
				.param("type", "ID_PROOF")
				.with(user("999").roles("ADMIN")))
				.andExpect(status().isNotFound());
	}

	@Test
	void roster_emptyForNewNanny() throws Exception {
		Nanny nanny = createNanny("Roster Nanny", "+9190006" + (System.nanoTime() % 100000));

		mockMvc.perform(get("/api/v1/admin/nannies/" + nanny.getId() + "/roster").with(user("999").roles("ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items", hasSize(0)));
	}

	@Test
	void reviews_emptyForNewNanny() throws Exception {
		Nanny nanny = createNanny("Review Nanny", "+9190007" + (System.nanoTime() % 100000));

		mockMvc.perform(get("/api/v1/admin/nannies/" + nanny.getId() + "/reviews").with(user("999").roles("ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items", hasSize(0)));
	}

	private Review createReview(Nanny nanny, short rating, String comment) {
		User parentUser = userRepository.save(User.builder()
				.name("Review Parent").email("review-parent-" + System.nanoTime() + "@example.com")
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.EMAIL).userType(UserType.PARENT)
				.build());
		Parent parent = parentRepository.save(Parent.builder().user(parentUser).firstName("Review").lastName("Parent").build());
		ServiceType childcare = serviceTypeRepository.findByCode("childcare").orElseThrow();
		Booking booking = bookingRepository.save(Booking.builder()
				.parent(parent).nanny(nanny).serviceType(childcare)
				.startTime(Instant.now().minusSeconds(7200)).endTime(Instant.now().minusSeconds(3600))
				.status(BookingStatus.COMPLETED)
				.build());
		return reviewRepository.save(Review.builder().booking(booking).parent(parent).nanny(nanny).rating(rating).comment(comment).build());
	}

	@Test
	void moderateReview_hideThenRestore_updatesStatusAndExcludesFromRatingWhileHidden() throws Exception {
		Nanny nanny = createNanny("Moderated Nanny", "+9190009" + (System.nanoTime() % 100000));
		Review review = createReview(nanny, (short) 1, "fake and abusive");

		mockMvc.perform(post("/api/v1/admin/nannies/" + nanny.getId() + "/reviews/" + review.getId() + "/status")
				.with(user("999").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"HIDDEN\",\"reason\":\"Reported as fake by three other families\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status", is("HIDDEN")))
				.andExpect(jsonPath("$.moderationReason", is("Reported as fake by three other families")));

		// Still visible to ops in the moderation list...
		mockMvc.perform(get("/api/v1/admin/nannies/" + nanny.getId() + "/reviews").with(user("999").roles("ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items", hasSize(1)))
				.andExpect(jsonPath("$.items[0].status", is("HIDDEN")));

		// ...but excluded from the public rating average.
		mockMvc.perform(get("/api/v1/admin/nannies/" + nanny.getId()).with(user("999").roles("ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.reviewCount", is(0)));

		// Restoring it brings the rating back.
		mockMvc.perform(post("/api/v1/admin/nannies/" + nanny.getId() + "/reviews/" + review.getId() + "/status")
				.with(user("999").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"VISIBLE\",\"reason\":\"Re-reviewed, not actually abusive\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status", is("VISIBLE")));

		mockMvc.perform(get("/api/v1/admin/nannies/" + nanny.getId()).with(user("999").roles("ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.reviewCount", is(1)));
	}

	@Test
	void moderateReview_blankReason_isBadRequest() throws Exception {
		Nanny nanny = createNanny("Blank Reason Nanny", "+9190010" + (System.nanoTime() % 100000));
		Review review = createReview(nanny, (short) 5, "great nanny");

		mockMvc.perform(post("/api/v1/admin/nannies/" + nanny.getId() + "/reviews/" + review.getId() + "/status")
				.with(user("999").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"HIDDEN\",\"reason\":\"\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void moderateReview_reviewBelongsToDifferentNanny_isNotFound() throws Exception {
		Nanny nanny = createNanny("Owner Nanny", "+9190011" + (System.nanoTime() % 100000));
		Nanny otherNanny = createNanny("Other Nanny", "+9190012" + (System.nanoTime() % 100000));
		Review review = createReview(nanny, (short) 4, "fine");

		mockMvc.perform(post("/api/v1/admin/nannies/" + otherNanny.getId() + "/reviews/" + review.getId() + "/status")
				.with(user("999").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"HIDDEN\",\"reason\":\"test\"}"))
				.andExpect(status().isNotFound());
	}

}
