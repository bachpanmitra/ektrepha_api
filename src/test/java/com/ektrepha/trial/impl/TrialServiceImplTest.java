package com.ektrepha.trial.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;

import com.ektrepha.config.properties.AppProperties;
import com.ektrepha.exception.TrialRevokedException;
import com.ektrepha.model.Booking;
import com.ektrepha.model.Parent;
import com.ektrepha.model.ParentTrial;
import com.ektrepha.model.ServiceType;
import com.ektrepha.model.TrialEndedReason;
import com.ektrepha.model.TrialStatus;
import com.ektrepha.model.User;
import com.ektrepha.parent.impl.ParentResolver;
import com.ektrepha.payment.service.PaymentGatewayService;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.ParentRepository;
import com.ektrepha.repository.ParentTrialRepository;
import com.ektrepha.trial.TrialHashUtil;
import com.ektrepha.trial.service.TrialAnalyticsService;
import com.ektrepha.trial.service.TrialService;

/**
 * Pure-unit coverage of FIRST5_FEE_WAIVER's eligibility/free-hours-cap/abuse logic, with every
 * collaborator mocked (no Spring context, no DB) - same style as {@code RateLimiterServiceImplTest}.
 * {@link PlatformTransactionManager} is mocked rather than real; {@link TransactionTemplate}
 * tolerates a mock that returns null from getTransaction() since none of this logic reads the
 * TransactionStatus it's handed.
 */
@ExtendWith(MockitoExtension.class)
class TrialServiceImplTest {

	private static final ZoneId INDIA_ZONE = ZoneId.of("Asia/Kolkata");
	private static final String SALT = "test-salt";
	private static final BigDecimal FREE_HOURS_CAP = new BigDecimal("16.00");
	private static final int CARE_DATE_LIMIT_DAYS = 14;

	@Mock
	private ParentTrialRepository parentTrialRepository;
	@Mock
	private ParentRepository parentRepository;
	@Mock
	private BookingRepository bookingRepository;
	@Mock
	private ParentResolver parentResolver;
	@Mock
	private PaymentGatewayService paymentGatewayService;
	@Mock
	private TrialAnalyticsService analyticsService;
	@Mock
	private TrialEmailServiceImpl trialEmailService;
	@Mock
	private PlatformTransactionManager transactionManager;

	private TrialServiceImpl service;
	private AppProperties appProperties;

	@BeforeEach
	void setUp() {
		AppProperties.Trial trial = new AppProperties.Trial(true, 7200L, FREE_HOURS_CAP, CARE_DATE_LIMIT_DAYS, 15, SALT);
		appProperties = new AppProperties(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, trial);
		service = new TrialServiceImpl(parentTrialRepository, parentRepository, bookingRepository, parentResolver,
				paymentGatewayService, analyticsService, trialEmailService, appProperties, transactionManager);
		org.mockito.Mockito.lenient().when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(null);
		// save() just persists-and-returns-the-same-row in real JPA - mirror that here since
		// endTrialAndNotify reassigns its local variable from the save() result.
		org.mockito.Mockito.lenient().when(parentTrialRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
	}

	// -------------------------------------------------------------- onFirstOtpVerification

	@Test
	void onFirstOtpVerification_disabled_doesNothing() {
		AppProperties.Trial disabled = new AppProperties.Trial(false, 7200L, FREE_HOURS_CAP, CARE_DATE_LIMIT_DAYS, 15, SALT);
		AppProperties disabledProps = new AppProperties(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, disabled);
		TrialServiceImpl disabledService = new TrialServiceImpl(parentTrialRepository, parentRepository, bookingRepository, parentResolver,
				paymentGatewayService, analyticsService, trialEmailService, disabledProps, transactionManager);

		disabledService.onFirstOtpVerification(User.builder().id(1L).phone("+911234567890").build(), "device-1");

		verifyNoInteractions(parentTrialRepository, parentResolver);
	}

	@Test
	void onFirstOtpVerification_eligible_createsActiveTrial() {
		User user = User.builder().id(1L).phone("+911234567890").build();
		Parent parent = Parent.builder().id(10L).user(user).build();
		when(parentResolver.resolveOrCreate(1L)).thenReturn(parent);
		when(parentTrialRepository.existsByPhoneHash(anyString())).thenReturn(false);
		when(parentTrialRepository.existsByDeviceIdHash(anyString())).thenReturn(false);
		when(bookingRepository.countByParentId(10L)).thenReturn(0L);

		service.onFirstOtpVerification(user, "device-1");

		ArgumentCaptor<ParentTrial> captor = ArgumentCaptor.forClass(ParentTrial.class);
		verify(parentTrialRepository).save(captor.capture());
		ParentTrial saved = captor.getValue();
		assertThat(saved.getStatus()).isEqualTo(TrialStatus.ACTIVE);
		assertThat(saved.getParentId()).isEqualTo(10L);
		assertThat(saved.getPhoneHash()).isEqualTo(TrialHashUtil.sha256Hex("+911234567890", SALT));
		assertThat(saved.getDeviceIdHash()).isEqualTo(TrialHashUtil.sha256Hex("device-1", SALT));
		assertThat(saved.getEndsAt()).isCloseTo(saved.getStartedAt().plus(Duration.ofMinutes(7200)), org.assertj.core.api.Assertions.within(java.time.Duration.ofSeconds(2)));
	}

	@Test
	void onFirstOtpVerification_phoneAlreadySeen_createsIneligibleTrial() {
		User user = User.builder().id(1L).phone("+911234567890").build();
		Parent parent = Parent.builder().id(10L).user(user).build();
		when(parentResolver.resolveOrCreate(1L)).thenReturn(parent);
		when(parentTrialRepository.existsByPhoneHash(anyString())).thenReturn(true);

		service.onFirstOtpVerification(user, "device-1");

		ArgumentCaptor<ParentTrial> captor = ArgumentCaptor.forClass(ParentTrial.class);
		verify(parentTrialRepository).save(captor.capture());
		assertThat(captor.getValue().getStatus()).isEqualTo(TrialStatus.INELIGIBLE);
	}

	@Test
	void onFirstOtpVerification_hasPastBookings_createsIneligibleTrial() {
		User user = User.builder().id(1L).phone("+911234567890").build();
		Parent parent = Parent.builder().id(10L).user(user).build();
		when(parentResolver.resolveOrCreate(1L)).thenReturn(parent);
		when(parentTrialRepository.existsByPhoneHash(anyString())).thenReturn(false);
		when(parentTrialRepository.existsByDeviceIdHash(anyString())).thenReturn(false);
		when(bookingRepository.countByParentId(10L)).thenReturn(1L);

		service.onFirstOtpVerification(user, "device-1");

		ArgumentCaptor<ParentTrial> captor = ArgumentCaptor.forClass(ParentTrial.class);
		verify(parentTrialRepository).save(captor.capture());
		assertThat(captor.getValue().getStatus()).isEqualTo(TrialStatus.INELIGIBLE);
	}

	// -------------------------------------------------------------- evaluateForQuote

	@Test
	void evaluateForQuote_noTrialRow_returnsEmpty() {
		when(parentTrialRepository.findByParentId(10L)).thenReturn(Optional.empty());

		Optional<TrialService.TrialQuoteApplication> result = service.evaluateForQuote(10L, 800L, 100L, 80L, LocalDate.now(INDIA_ZONE));

		assertThat(result).isEmpty();
	}

	@Test
	void evaluateForQuote_active_waivesSubtotalAndFeeRegardlessOfHours() {
		// A booking whose hours would exceed the cap still gets its subtotal+fee waived in full at
		// quote time - the cap only governs when the trial ends, not whether/how much of this
		// booking is charged. GST (the 80L) is never part of the discount - it's the only thing
		// the parent still pays.
		ParentTrial trial = activeTrial(new BigDecimal("15.00"));
		when(parentTrialRepository.findByParentId(10L)).thenReturn(Optional.of(trial));

		Optional<TrialService.TrialQuoteApplication> result = service.evaluateForQuote(10L, 800L, 100L, 80L, LocalDate.now(INDIA_ZONE).plusDays(1));

		assertThat(result).isPresent();
		assertThat(result.get().trialId()).isEqualTo(trial.getId());
		assertThat(result.get().discountPaise()).isEqualTo(900L);
	}

	@Test
	void evaluateForQuote_doesNotConsultFreeHoursUsed() {
		// In practice settleBookingCheckout flips status away from ACTIVE the moment the cap is
		// reached, so a trial sitting at/past the cap while still ACTIVE shouldn't normally happen.
		// This pins down that evaluateForQuote itself never looks at freeHoursUsed at all - only
		// status/endsAt/careDate gate it - so a future change doesn't silently reintroduce a
		// partial-discount path without a corresponding spec update.
		ParentTrial trial = activeTrial(FREE_HOURS_CAP);
		when(parentTrialRepository.findByParentId(10L)).thenReturn(Optional.of(trial));

		Optional<TrialService.TrialQuoteApplication> result = service.evaluateForQuote(10L, 800L, 100L, 80L, LocalDate.now(INDIA_ZONE));

		assertThat(result).isPresent();
		assertThat(result.get().discountPaise()).isEqualTo(900L);
	}

	@Test
	void evaluateForQuote_careDateBeyondLimit_returnsEmpty() {
		ParentTrial trial = activeTrial(BigDecimal.ZERO);
		when(parentTrialRepository.findByParentId(10L)).thenReturn(Optional.of(trial));

		Optional<TrialService.TrialQuoteApplication> result = service.evaluateForQuote(
				10L, 800L, 100L, 80L, LocalDate.now(INDIA_ZONE).plusDays(CARE_DATE_LIMIT_DAYS + 1));

		assertThat(result).isEmpty();
	}

	@Test
	void evaluateForQuote_trialAlreadyExpired_returnsEmpty() {
		ParentTrial trial = activeTrial(BigDecimal.ZERO);
		trial.setEndsAt(Instant.now().minusSeconds(60));
		when(parentTrialRepository.findByParentId(10L)).thenReturn(Optional.of(trial));

		Optional<TrialService.TrialQuoteApplication> result = service.evaluateForQuote(10L, 800L, 100L, 80L, LocalDate.now(INDIA_ZONE));

		assertThat(result).isEmpty();
	}

	@Test
	void evaluateForQuote_ineligibleTrial_returnsEmpty() {
		ParentTrial trial = activeTrial(BigDecimal.ZERO);
		trial.setStatus(TrialStatus.INELIGIBLE);
		when(parentTrialRepository.findByParentId(10L)).thenReturn(Optional.of(trial));

		Optional<TrialService.TrialQuoteApplication> result = service.evaluateForQuote(10L, 800L, 100L, 80L, LocalDate.now(INDIA_ZONE));

		assertThat(result).isEmpty();
	}

	@Test
	void evaluateForQuote_endedTrial_returnsEmpty() {
		ParentTrial trial = activeTrial(FREE_HOURS_CAP);
		trial.setStatus(TrialStatus.ENDED);
		trial.setEndedReason(TrialEndedReason.CAP_HOURS);
		when(parentTrialRepository.findByParentId(10L)).thenReturn(Optional.of(trial));

		Optional<TrialService.TrialQuoteApplication> result = service.evaluateForQuote(10L, 800L, 100L, 80L, LocalDate.now(INDIA_ZONE));

		assertThat(result).isEmpty();
	}

	// -------------------------------------------------------------- settleBookingCheckout

	@Test
	void settleBookingCheckout_noTrialOnBooking_isNoop() {
		Booking booking = Booking.builder().id(1L).trialId(null).build();

		service.settleBookingCheckout(booking, "order_1");

		verifyNoInteractions(parentTrialRepository, paymentGatewayService, analyticsService);
	}

	@Test
	void settleBookingCheckout_belowCap_incrementsHoursAndStaysActive() {
		ParentTrial trial = activeTrial(BigDecimal.ZERO);
		Booking booking = bookingWithTrial(trial.getId(), 980L, Duration.ofHours(3));
		when(parentTrialRepository.findByIdForUpdate(trial.getId())).thenReturn(Optional.of(trial));
		when(paymentGatewayService.fetchPaymentFingerprint("order_1")).thenReturn(Optional.empty());

		service.settleBookingCheckout(booking, "order_1");

		ArgumentCaptor<ParentTrial> captor = ArgumentCaptor.forClass(ParentTrial.class);
		verify(parentTrialRepository).save(captor.capture());
		assertThat(captor.getValue().getStatus()).isEqualTo(TrialStatus.ACTIVE);
		assertThat(captor.getValue().getFreeHoursUsed()).isEqualByComparingTo("3.00");
		verify(analyticsService).track(eq("trial_booking_confirmed"), any());
	}

	@Test
	void settleBookingCheckout_hoursPushTotalPastCap_endsTrial() {
		// 15h already used + a 5h booking = 20h, past the 16h cap - this booking is still fully
		// honored free (asserted via the unchanged totalAmount elsewhere); only the trial's status
		// is affected here.
		ParentTrial trial = activeTrial(new BigDecimal("15.00"));
		Booking booking = bookingWithTrial(trial.getId(), 980L, Duration.ofHours(5));
		when(parentTrialRepository.findByIdForUpdate(trial.getId())).thenReturn(Optional.of(trial));
		when(paymentGatewayService.fetchPaymentFingerprint("order_1")).thenReturn(Optional.empty());

		service.settleBookingCheckout(booking, "order_1");

		ArgumentCaptor<ParentTrial> captor = ArgumentCaptor.forClass(ParentTrial.class);
		verify(parentTrialRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
		ParentTrial lastSaved = captor.getValue();
		assertThat(lastSaved.getStatus()).isEqualTo(TrialStatus.ENDED);
		assertThat(lastSaved.getEndedReason()).isEqualTo(TrialEndedReason.CAP_HOURS);
		assertThat(lastSaved.getFreeHoursUsed()).isEqualByComparingTo("20.00");
		verify(analyticsService).track(eq("trial_ended"), any());
	}

	@Test
	void settleBookingCheckout_hoursExactlyMeetCap_endsTrial() {
		ParentTrial trial = activeTrial(new BigDecimal("14.00"));
		Booking booking = bookingWithTrial(trial.getId(), 980L, Duration.ofHours(2));
		when(parentTrialRepository.findByIdForUpdate(trial.getId())).thenReturn(Optional.of(trial));
		when(paymentGatewayService.fetchPaymentFingerprint("order_1")).thenReturn(Optional.empty());

		service.settleBookingCheckout(booking, "order_1");

		ArgumentCaptor<ParentTrial> captor = ArgumentCaptor.forClass(ParentTrial.class);
		verify(parentTrialRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
		assertThat(captor.getValue().getStatus()).isEqualTo(TrialStatus.ENDED);
		assertThat(captor.getValue().getEndedReason()).isEqualTo(TrialEndedReason.CAP_HOURS);
	}

	@Test
	void settleBookingCheckout_duplicatePaymentFingerprint_revokesTrial() {
		ParentTrial trial = activeTrial(BigDecimal.ZERO);
		Booking booking = bookingWithTrial(trial.getId(), 980L, Duration.ofHours(3));
		when(parentTrialRepository.findByIdForUpdate(trial.getId())).thenReturn(Optional.of(trial));
		when(paymentGatewayService.fetchPaymentFingerprint("order_1")).thenReturn(Optional.of("card:tok_abc"));
		when(parentTrialRepository.existsByPaymentFingerprintAndIdNot(anyString(), eq(trial.getId()))).thenReturn(true);

		service.settleBookingCheckout(booking, "order_1");

		ArgumentCaptor<ParentTrial> captor = ArgumentCaptor.forClass(ParentTrial.class);
		verify(parentTrialRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
		ParentTrial lastSaved = captor.getValue();
		assertThat(lastSaved.getStatus()).isEqualTo(TrialStatus.REVOKED);
		assertThat(lastSaved.getEndedReason()).isEqualTo(TrialEndedReason.ABUSE);
	}

	// -------------------------------------------------------------- guardAddressOrRevoke

	@Test
	void guardAddressOrRevoke_addressAlreadyClaimedByAnotherTrial_revokesAndThrows() {
		ParentTrial trial = activeTrial(BigDecimal.ZERO);
		when(parentTrialRepository.findByIdForUpdate(trial.getId())).thenReturn(Optional.of(trial));
		when(parentTrialRepository.existsByAddressHashAndIdNot(anyString(), eq(trial.getId()))).thenReturn(true);

		org.junit.jupiter.api.Assertions.assertThrows(TrialRevokedException.class,
				() -> service.guardAddressOrRevoke(trial.getId(), "123 test st|600001"));

		ArgumentCaptor<ParentTrial> captor = ArgumentCaptor.forClass(ParentTrial.class);
		verify(parentTrialRepository).save(captor.capture());
		assertThat(captor.getValue().getStatus()).isEqualTo(TrialStatus.REVOKED);
	}

	@Test
	void guardAddressOrRevoke_newAddress_claimsItWithoutThrowing() {
		ParentTrial trial = activeTrial(BigDecimal.ZERO);
		when(parentTrialRepository.findByIdForUpdate(trial.getId())).thenReturn(Optional.of(trial));
		when(parentTrialRepository.existsByAddressHashAndIdNot(anyString(), eq(trial.getId()))).thenReturn(false);

		service.guardAddressOrRevoke(trial.getId(), "123 test st|600001");

		ArgumentCaptor<ParentTrial> captor = ArgumentCaptor.forClass(ParentTrial.class);
		verify(parentTrialRepository).save(captor.capture());
		assertThat(captor.getValue().getAddressHash()).isNotNull();
	}

	// -------------------------------------------------------------- expireIfStillActive

	@Test
	void expireIfStillActive_pastEndsAt_endsTrial() {
		ParentTrial trial = activeTrial(BigDecimal.ZERO);
		trial.setEndsAt(Instant.now().minusSeconds(1));
		when(parentTrialRepository.findByIdForUpdate(trial.getId())).thenReturn(Optional.of(trial));

		service.expireIfStillActive(trial.getId());

		ArgumentCaptor<ParentTrial> captor = ArgumentCaptor.forClass(ParentTrial.class);
		verify(parentTrialRepository).save(captor.capture());
		assertThat(captor.getValue().getStatus()).isEqualTo(TrialStatus.ENDED);
		assertThat(captor.getValue().getEndedReason()).isEqualTo(TrialEndedReason.EXPIRED);
	}

	@Test
	void expireIfStillActive_notYetDue_doesNothing() {
		ParentTrial trial = activeTrial(BigDecimal.ZERO);
		trial.setEndsAt(Instant.now().plusSeconds(3600));
		when(parentTrialRepository.findByIdForUpdate(trial.getId())).thenReturn(Optional.of(trial));

		service.expireIfStillActive(trial.getId());

		verify(parentTrialRepository, never()).save(any());
	}

	// -------------------------------------------------------------- fixtures

	private ParentTrial activeTrial(BigDecimal freeHoursUsed) {
		return ParentTrial.builder()
				.id(UUID.randomUUID()).parentId(10L).status(TrialStatus.ACTIVE)
				.startedAt(Instant.now()).endsAt(Instant.now().plusSeconds(3600))
				.phoneHash("hash").freeHoursUsed(freeHoursUsed)
				.build();
	}

	private Booking bookingWithTrial(UUID trialId, long trialDiscountPaise, Duration duration) {
		Instant start = Instant.now();
		return Booking.builder()
				.id(1L).trialId(trialId).trialDiscountPaise(trialDiscountPaise)
				.startTime(start).endTime(start.plus(duration))
				.serviceType(ServiceType.builder().code("childcare").build())
				.build();
	}

}
