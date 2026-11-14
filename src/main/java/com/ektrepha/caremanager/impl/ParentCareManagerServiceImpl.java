package com.ektrepha.caremanager.impl;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.caremanager.OutboxEventWriter;
import com.ektrepha.caremanager.ShiftWindow;
import com.ektrepha.caremanager.dto.request.LogContactRequest;
import com.ektrepha.caremanager.dto.request.RateVisitRequest;
import com.ektrepha.caremanager.dto.request.ReasonRequest;
import com.ektrepha.caremanager.dto.response.ChangeRequestResponse;
import com.ektrepha.caremanager.dto.response.MyCareManagerResponse;
import com.ektrepha.caremanager.dto.response.RateVisitResponse;
import com.ektrepha.caremanager.event.CmChangeRequestedPayload;
import com.ektrepha.caremanager.event.NannyCheckedInPayload;
import com.ektrepha.caremanager.event.ParentRequestPayload;
import com.ektrepha.caremanager.event.RatingSubmittedPayload;
import com.ektrepha.caremanager.service.ParentCareManagerService;
import com.ektrepha.exception.BookingNotFoundException;
import com.ektrepha.exception.CmResourceNotFoundException;
import com.ektrepha.exception.CmValidationException;
import com.ektrepha.exception.DuplicateRequestException;
import com.ektrepha.exception.ParentNotFoundException;
import com.ektrepha.model.Booking;
import com.ektrepha.model.BookingStatus;
import com.ektrepha.model.CareManager;
import com.ektrepha.model.CmChangeRequest;
import com.ektrepha.model.CmChangeRequestKind;
import com.ektrepha.model.CmContact;
import com.ektrepha.model.CmContactChannel;
import com.ektrepha.model.CmContactDirection;
import com.ektrepha.model.CmContactSource;
import com.ektrepha.model.CmDutyShift;
import com.ektrepha.model.CmTaskStatus;
import com.ektrepha.model.Parent;
import com.ektrepha.model.Review;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.CmChangeRequestRepository;
import com.ektrepha.repository.CmContactRepository;
import com.ektrepha.repository.CmDutyShiftRepository;
import com.ektrepha.repository.ParentRepository;
import com.ektrepha.repository.ReviewRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ParentCareManagerServiceImpl implements ParentCareManagerService {

	private static final List<BookingStatus> ACTIVE_STATUSES = List.of(BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS);

	private final ParentRepository parentRepository;
	private final CmDutyShiftRepository cmDutyShiftRepository;
	private final CmContactRepository cmContactRepository;
	private final CmChangeRequestRepository cmChangeRequestRepository;
	private final BookingRepository bookingRepository;
	private final ReviewRepository reviewRepository;
	private final OutboxEventWriter outboxEventWriter;
	private final Clock clock;

	@Override
	@Transactional(readOnly = true)
	public MyCareManagerResponse getMyCareManager(Long parentUserId) {
		Parent parent = parentRepository.findByUserId(parentUserId)
				.orElseThrow(() -> new ParentNotFoundException("No parent for user " + parentUserId));
		CareManager ownCm = parent.getCareManager();
		if (ownCm == null) {
			log.error("Parent {} has no Care Manager assigned — should not happen after backfill", parent.getId());
			throw new CmResourceNotFoundException("No Care Manager assigned yet");
		}

		Instant now = Instant.now(clock);
		boolean inShift = ShiftWindow.isWithin(ownCm.getShiftStart(), ownCm.getShiftEnd(), now);
		CareManager contactCm = ownCm;
		if (!inShift) {
			List<CmDutyShift> onDuty = cmDutyShiftRepository.findAnyCoveringNow(now);
			if (!onDuty.isEmpty()) {
				contactCm = onDuty.get(0).getCareManager();
			}
			// Else: nobody on duty — keep the parent's own CM's number, per spec.
		}

		String whatsappMessage = buildWhatsappMessage(ownCm, parent);
		MyCareManagerResponse.Contact contact = new MyCareManagerResponse.Contact(
				"tel:" + contactCm.getPhone(),
				"https://wa.me/" + stripPlus(contactCm.getPhone()) + "?text=" + whatsappMessage,
				"mailto:" + ownCm.getEmail());

		MyCareManagerResponse.CareManagerCard card = new MyCareManagerResponse.CareManagerCard(
				ownCm.getPublicId(), ownCm.getName(), initials(ownCm.getName()), ownCm.getPhotoUrl(),
				"Reach them 24x7, even when the help desk is closed", contact);
		return new MyCareManagerResponse(card);
	}

	@Override
	@Transactional
	public void logContact(Long parentUserId, LogContactRequest request) {
		Parent parent = parentRepository.findByUserId(parentUserId)
				.orElseThrow(() -> new ParentNotFoundException("No parent for user " + parentUserId));
		CareManager ownCm = parent.getCareManager();
		if (ownCm == null) {
			return;
		}

		boolean askCmChip = request.channel() == LogContactRequest.Channel.ASK_CM_CHIP;
		CmContactChannel channel = askCmChip ? CmContactChannel.WHATSAPP : CmContactChannel.valueOf(request.channel().name());
		String sourceScreen = askCmChip ? "BOOKING_CARD" : request.sourceScreen().name();

		cmContactRepository.save(CmContact.builder()
				.parent(parent)
				.careManager(ownCm)
				.channel(channel)
				.direction(CmContactDirection.INBOUND)
				.source(CmContactSource.APP_TAP)
				.sourceScreen(sourceScreen)
				.build());

		if (askCmChip) {
			outboxEventWriter.write("CmContactRequested", new ParentRequestPayload(parent.getId()));
		}
	}

	@Override
	@Transactional
	public ChangeRequestResponse requestCareManagerChange(Long parentUserId, ReasonRequest request) {
		Parent parent = parentRepository.findByUserId(parentUserId)
				.orElseThrow(() -> new ParentNotFoundException("No parent for user " + parentUserId));
		cmChangeRequestRepository.findByParentIdAndKindAndStatus(parent.getId(), CmChangeRequestKind.CM, CmTaskStatus.OPEN)
				.ifPresent(existing -> {
					throw new DuplicateRequestException("You already have an open Care Manager change request");
				});

		CmChangeRequest changeRequest = CmChangeRequest.builder()
				.kind(CmChangeRequestKind.CM)
				.parent(parent)
				.reason(request.reason())
				.build();
		cmChangeRequestRepository.save(changeRequest);

		outboxEventWriter.write("CmChangeRequested", new CmChangeRequestedPayload(changeRequest.getId(), parent.getId()));
		return toChangeRequestResponse(changeRequest);
	}

	@Override
	@Transactional
	public RateVisitResponse rateVisit(Long parentUserId, String bookingPublicId, RateVisitRequest request) {
		Parent parent = parentRepository.findByUserId(parentUserId)
				.orElseThrow(() -> new ParentNotFoundException("No parent for user " + parentUserId));
		Booking booking = bookingRepository.findByPublicId(bookingPublicId)
				.filter(b -> b.getParent().getId().equals(parent.getId()))
				.orElseThrow(() -> new BookingNotFoundException("No booking with id " + bookingPublicId));
		if (booking.getStatus() != BookingStatus.COMPLETED) {
			throw new CmValidationException("Only a checked-out visit can be rated");
		}
		if (reviewRepository.existsByBookingId(booking.getId())) {
			throw new DuplicateRequestException("This visit has already been rated");
		}

		Review review = Review.builder()
				.booking(booking)
				.parent(parent)
				.nanny(booking.getNanny())
				.rating(request.score().shortValue())
				.comment(request.comment())
				.build();
		reviewRepository.save(review);

		Long nannyId = booking.getNanny() == null ? null : booking.getNanny().getId();
		outboxEventWriter.write("RatingSubmitted", new RatingSubmittedPayload(booking.getId(), parent.getId(), nannyId, request.score()));

		boolean cmWillCall = request.score() <= 3;
		return new RateVisitResponse(request.score(), cmWillCall, cmWillCall ? 120 : null);
	}

	@Override
	@Transactional
	public ChangeRequestResponse requestNannyChange(Long parentUserId, String bookingPublicId, ReasonRequest request) {
		Parent parent = parentRepository.findByUserId(parentUserId)
				.orElseThrow(() -> new ParentNotFoundException("No parent for user " + parentUserId));
		Booking booking = bookingRepository.findByPublicId(bookingPublicId)
				.filter(b -> b.getParent().getId().equals(parent.getId()))
				.orElseThrow(() -> new BookingNotFoundException("No booking with id " + bookingPublicId));
		cmChangeRequestRepository.findByParentIdAndKindAndStatus(parent.getId(), CmChangeRequestKind.NANNY, CmTaskStatus.OPEN)
				.ifPresent(existing -> {
					throw new DuplicateRequestException("You already have an open nanny change request");
				});

		CmChangeRequest changeRequest = CmChangeRequest.builder()
				.kind(CmChangeRequestKind.NANNY)
				.parent(parent)
				.booking(booking)
				.reason(request.reason())
				.build();
		cmChangeRequestRepository.save(changeRequest);

		outboxEventWriter.write("NannyChangeRequested", new ParentRequestPayload(parent.getId()));
		return toChangeRequestResponse(changeRequest);
	}

	private ChangeRequestResponse toChangeRequestResponse(CmChangeRequest cr) {
		return new ChangeRequestResponse(
				cr.getPublicId(),
				com.ektrepha.caremanager.CmPersonRefMapper.of(cr.getParent()),
				cr.getBooking() == null ? null : cr.getBooking().getPublicId(),
				cr.getReason(), cr.getStatus(), cr.getCreatedAt());
	}

	private String initials(String name) {
		String[] words = name.trim().split("\\s+");
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < Math.min(2, words.length); i++) {
			if (!words[i].isEmpty()) {
				sb.append(Character.toUpperCase(words[i].charAt(0)));
			}
		}
		return sb.toString();
	}

	private String stripPlus(String e164Phone) {
		return e164Phone.startsWith("+") ? e164Phone.substring(1) : e164Phone;
	}

	private String buildWhatsappMessage(CareManager cm, Parent parent) {
		String cmFirstName = cm.getName().trim().split("\\s+")[0];
		String parentName = ((parent.getFirstName() == null ? "" : parent.getFirstName()) + " "
				+ (parent.getLastName() == null ? "" : parent.getLastName())).trim();
		String message = "Hi " + cmFirstName + ", this is " + (parentName.isEmpty() ? "your parent app" : parentName) + ".";

		Optional<Booking> active = bookingRepository.findFirstByParentIdAndStatusInOrderByStartTimeAsc(parent.getId(), ACTIVE_STATUSES);
		if (active.isPresent()) {
			message += " About booking " + active.get().getPublicId() + ".";
		}
		return urlEncode(message);
	}

	private String urlEncode(String value) {
		try {
			return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20");
		} catch (UnsupportedEncodingException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
