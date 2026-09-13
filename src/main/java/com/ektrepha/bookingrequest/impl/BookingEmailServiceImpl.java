package com.ektrepha.bookingrequest.impl;

import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.ektrepha.bookingrequest.service.BookingEmailService;
import com.ektrepha.config.properties.AppProperties;
import com.ektrepha.email.AbstractEmailSender;
import com.ektrepha.model.BookingRequest;
import com.ektrepha.model.ServiceType;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class BookingEmailServiceImpl extends AbstractEmailSender implements BookingEmailService {

	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy");
	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a");

	public BookingEmailServiceImpl(AppProperties appProperties) {
		super(appProperties);
	}

	@Override
	public void sendBookingConfirmationEmail(BookingRequest bookingRequest, ServiceType serviceType) {
		String toEmail = bookingRequest.getUser().getEmail();
		if (!StringUtils.hasText(toEmail)) {
			log.info("Skipping booking confirmation email for booking request {} — user has no email on file", bookingRequest.getId());
			return;
		}

		String html = """
				<p>Hi %s,</p>
				<p>We've received your booking request for <strong>%s</strong> on <strong>%s</strong>,
				from <strong>%s</strong> to <strong>%s</strong>.</p>
				<p>Quoted total: <strong>%s</strong></p>
				<p>We'll be in touch shortly to confirm.</p>
				"""
				.formatted(
						bookingRequest.getUser().getName(),
						serviceType.getName(),
						bookingRequest.getBookingDate().format(DATE_FORMAT),
						bookingRequest.getStartTime().format(TIME_FORMAT),
						bookingRequest.getEndTime().format(TIME_FORMAT),
						bookingRequest.getQuotedTotal() != null ? "₹" + bookingRequest.getQuotedTotal() : "to be confirmed");

		boolean sent = send(toEmail, "Your Ektrepha booking request is confirmed", html);
		if (sent) {
			log.info("Sent booking confirmation email to {} for booking request {}", toEmail, bookingRequest.getId());
		}
	}

}
