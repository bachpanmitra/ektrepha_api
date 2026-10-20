package com.ektrepha.trial.impl;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.ektrepha.config.properties.AppProperties;
import com.ektrepha.email.AbstractEmailSender;

import lombok.extern.slf4j.Slf4j;

/** Sends the "your trial has ended" email — only ever called when the parent used at least some free hours (see TrialServiceImpl#endTrialAndNotify). */
@Slf4j
@Service
public class TrialEmailServiceImpl extends AbstractEmailSender {

	public TrialEmailServiceImpl(AppProperties appProperties) {
		super(appProperties);
	}

	public void sendTrialEndedEmail(String toEmail, String firstName, BigDecimal freeHoursUsed) {
		if (!StringUtils.hasText(toEmail)) {
			log.info("Skipping trial-ended email — parent has no email on file");
			return;
		}

		String greeting = StringUtils.hasText(firstName) ? firstName : "there";
		String html = """
				<p>Hi %s,</p>
				<p>Your free trial has ended — thanks for trying Ektrepha! You used <strong>%s</strong>
				free hours of care with zero charge.</p>
				<p>Every booking from here on is charged at the normal rate, same great care.</p>
				"""
				.formatted(greeting, freeHoursUsed.stripTrailingZeros().toPlainString());

		boolean sent = send(toEmail, "Your Ektrepha free trial has ended", html);
		if (sent) {
			log.info("Sent trial-ended email to {}", toEmail);
		}
	}

}
