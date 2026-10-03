package com.ektrepha.hourlycare.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ektrepha.activity.service.OrderActivityService;
import com.ektrepha.hourlycare.dto.response.BookingStatusResponse;
import com.ektrepha.model.Booking;
import com.ektrepha.model.BookingStatus;
import com.ektrepha.model.Parent;
import com.ektrepha.model.PaymentMethod;
import com.ektrepha.model.PaymentStatus;
import com.ektrepha.model.PaymentTransaction;
import com.ektrepha.model.User;
import com.ektrepha.parent.impl.AddressResponseMapper;
import com.ektrepha.payment.dto.GatewayPaymentStatus;
import com.ektrepha.payment.service.PaymentGatewayService;
import com.ektrepha.pricing.service.PricingService;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.CaregiverZoneMappingRepository;
import com.ektrepha.repository.NannyRepository;
import com.ektrepha.repository.ParentAddressRepository;
import com.ektrepha.repository.ParentChildRepository;
import com.ektrepha.repository.ParentRepository;
import com.ektrepha.repository.PaymentTransactionRepository;
import com.ektrepha.repository.ServiceTypeRepository;
import com.ektrepha.repository.ServiceabilityPincodeRepository;
import com.ektrepha.repository.UserRepository;

/**
 * Covers {@link HourlyCareServiceImpl#status}'s self-heal path for a booking stuck at
 * AWAITING_PAYMENT because the client's own Checkout confirm call never arrived (the exact symptom
 * reported for the Google Pay/PhonePe/Paytm/BHIM UPI app-switch flow - see the class comment on
 * {@code RazorpayWebhookController}: no webhook is registered yet to catch this server-to-server).
 * {@link PaymentGatewayService} is mocked so this runs without any real Razorpay account.
 */
@ExtendWith(MockitoExtension.class)
class HourlyCareServiceImplReconciliationTest {

	private static final Long USER_ID = 42L;
	private static final Long BOOKING_ID = 7L;
	private static final Long PAYMENT_ID = 99L;
	private static final String ORDER_ID = "order_abc123";

	@Mock
	private ParentRepository parentRepository;
	@Mock
	private BookingRepository bookingRepository;
	@Mock
	private NannyRepository nannyRepository;
	@Mock
	private ServiceTypeRepository serviceTypeRepository;
	@Mock
	private ParentAddressRepository parentAddressRepository;
	@Mock
	private ParentChildRepository parentChildRepository;
	@Mock
	private ServiceabilityPincodeRepository serviceabilityPincodeRepository;
	@Mock
	private CaregiverZoneMappingRepository caregiverZoneMappingRepository;
	@Mock
	private PaymentTransactionRepository paymentTransactionRepository;
	@Mock
	private PaymentGatewayService paymentGatewayService;
	@Mock
	private PricingService pricingService;
	@Mock
	private AddressResponseMapper addressResponseMapper;
	@Mock
	private OrderActivityService orderActivityService;
	@Mock
	private UserRepository userRepository;

	private HourlyCareServiceImpl service;
	private Parent parent;
	private Booking booking;
	private PaymentTransaction transaction;

	@BeforeEach
	void setUp() {
		service = new HourlyCareServiceImpl(parentRepository, bookingRepository, nannyRepository, serviceTypeRepository,
				parentAddressRepository, parentChildRepository, serviceabilityPincodeRepository,
				caregiverZoneMappingRepository, paymentTransactionRepository, paymentGatewayService, pricingService,
				addressResponseMapper, orderActivityService, userRepository);

		User user = User.builder().id(USER_ID).build();
		parent = Parent.builder().id(1L).user(user).build();

		booking = Booking.builder()
				.id(BOOKING_ID).parent(parent).status(BookingStatus.AWAITING_PAYMENT)
				.startTime(Instant.now()).endTime(Instant.now().plusSeconds(3600))
				.totalAmount(new BigDecimal("1100.00"))
				.build();

		transaction = PaymentTransaction.builder()
				.id(PAYMENT_ID).booking(booking).amount(new BigDecimal("1100.00"))
				.method(PaymentMethod.UPI).status(PaymentStatus.INITIATED).providerReference(ORDER_ID)
				.build();

		when(parentRepository.findByUserId(USER_ID)).thenReturn(Optional.of(parent));
		when(bookingRepository.findByIdAndParentId(BOOKING_ID, parent.getId())).thenReturn(Optional.of(booking));
	}

	@Test
	void status_capturedAtGateway_advancesBookingAndMarksPaymentSuccess() {
		when(paymentTransactionRepository.findFirstByBookingIdAndStatusOrderByIdDescForUpdate(BOOKING_ID, PaymentStatus.INITIATED))
				.thenReturn(Optional.of(transaction));
		when(paymentGatewayService.findLatestPayment(ORDER_ID))
				.thenReturn(Optional.of(new GatewayPaymentStatus("pay_xyz789", true)));
		when(paymentTransactionRepository.findFirstByBookingIdAndStatusOrderByIdDesc(BOOKING_ID, PaymentStatus.SUCCESS))
				.thenReturn(Optional.of(transaction));

		BookingStatusResponse response = service.status(USER_ID, BOOKING_ID);

		assertThat(transaction.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
		assertThat(transaction.getGatewayPaymentId()).isEqualTo("pay_xyz789");
		assertThat(booking.getStatus()).isEqualTo(BookingStatus.ASSIGNING_CAREGIVER);
		assertThat(response.status()).isEqualTo("ASSIGNING_CAREGIVER");
		verify(paymentTransactionRepository).save(transaction);
		verify(bookingRepository).save(booking);
	}

	@Test
	void status_failedAtGateway_marksPaymentFailedButLeavesBookingAwaitingPayment() {
		when(paymentTransactionRepository.findFirstByBookingIdAndStatusOrderByIdDescForUpdate(BOOKING_ID, PaymentStatus.INITIATED))
				.thenReturn(Optional.of(transaction));
		when(paymentGatewayService.findLatestPayment(ORDER_ID))
				.thenReturn(Optional.of(new GatewayPaymentStatus(null, false)));
		when(paymentTransactionRepository.findFirstByBookingIdAndStatusOrderByIdDesc(BOOKING_ID, PaymentStatus.SUCCESS))
				.thenReturn(Optional.empty());

		BookingStatusResponse response = service.status(USER_ID, BOOKING_ID);

		assertThat(transaction.getStatus()).isEqualTo(PaymentStatus.FAILED);
		assertThat(booking.getStatus()).isEqualTo(BookingStatus.AWAITING_PAYMENT);
		assertThat(response.status()).isEqualTo("AWAITING_PAYMENT");
		verify(bookingRepository, never()).save(any());
	}

	@Test
	void status_gatewayHasNoInfoYet_leavesTransactionPendingForNextPoll() {
		when(paymentTransactionRepository.findFirstByBookingIdAndStatusOrderByIdDescForUpdate(BOOKING_ID, PaymentStatus.INITIATED))
				.thenReturn(Optional.of(transaction));
		when(paymentGatewayService.findLatestPayment(ORDER_ID)).thenReturn(Optional.empty());
		when(paymentTransactionRepository.findFirstByBookingIdAndStatusOrderByIdDesc(BOOKING_ID, PaymentStatus.SUCCESS))
				.thenReturn(Optional.empty());

		service.status(USER_ID, BOOKING_ID);

		assertThat(transaction.getStatus()).isEqualTo(PaymentStatus.INITIATED);
		assertThat(booking.getStatus()).isEqualTo(BookingStatus.AWAITING_PAYMENT);
		verify(paymentTransactionRepository, never()).save(any());
	}

	@Test
	void status_bookingAlreadyPastAwaitingPayment_skipsGatewayLookupEntirely() {
		booking.setStatus(BookingStatus.ASSIGNING_CAREGIVER);
		when(paymentTransactionRepository.findFirstByBookingIdAndStatusOrderByIdDesc(BOOKING_ID, PaymentStatus.SUCCESS))
				.thenReturn(Optional.of(transaction));

		service.status(USER_ID, BOOKING_ID);

		verify(paymentGatewayService, never()).findLatestPayment(any());
		verify(paymentTransactionRepository, never())
				.findFirstByBookingIdAndStatusOrderByIdDescForUpdate(BOOKING_ID, PaymentStatus.INITIATED);
	}

}
