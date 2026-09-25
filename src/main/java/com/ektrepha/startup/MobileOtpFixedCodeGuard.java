package com.ektrepha.startup;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.ektrepha.config.properties.AppProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Validates the two mobile-OTP fixed-code bypasses at startup (see
 * {@link AppProperties.MobileOtp.FixedOtp} / {@link AppProperties.MobileOtp.ReviewAccount}):
 * <ul>
 * <li>{@code fixed-otp} (dev/stage general testing bypass) must never be enabled while the active
 * profile is prod — fails startup outright if it is.</li>
 * <li>{@code review-account} (a handful of allowlisted numbers — e.g. an App Store/Play Store
 * reviewer account — that IS allowed in prod) must have a real code and a non-empty allowlist
 * whenever enabled, in any profile, or startup fails as a misconfiguration.</li>
 * </ul>
 * Either bypass being active also logs a warning naming the exact allowlisted numbers, every time
 * the app boots, so it's never silently forgotten — especially in prod.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class MobileOtpFixedCodeGuard implements ApplicationRunner {

	private final Environment environment;
	private final AppProperties appProperties;

	@Override
	public void run(ApplicationArguments args) {
		boolean isProd = environment.matchesProfiles("prod");
		AppProperties.MobileOtp.FixedOtp fixedOtp = appProperties.mobileOtp().fixedOtp();
		AppProperties.MobileOtp.ReviewAccount reviewAccount = appProperties.mobileOtp().reviewAccount();

		if (fixedOtp.enabled() && isProd) {
			throw new IllegalStateException(
					"app.mobile-otp.fixed-otp.enabled=true while the active profile is 'prod'. "
							+ "The general fixed-OTP test bypass must never be enabled in production — refusing to start. "
							+ "For a production-safe reviewer/test account, use app.mobile-otp.review-account.* instead.");
		}
		if (fixedOtp.enabled()) {
			log.warn("Fixed-OTP test mode is ENABLED (app.mobile-otp.fixed-otp.enabled=true) for allowlisted numbers: {}",
					fixedOtp.allowedNumbers());
		}

		if (reviewAccount.enabled()) {
			List<String> numbers = reviewAccount.allowedNumbers();
			if (reviewAccount.code() == null || reviewAccount.code().isBlank() || numbers == null || numbers.isEmpty()) {
				throw new IllegalStateException(
						"app.mobile-otp.review-account.enabled=true but code/allowed-numbers is missing. "
								+ "Set both (a real code and at least one E.164 number) or leave this disabled — refusing to start.");
			}
			log.warn("Mobile OTP REVIEW-ACCOUNT bypass is ENABLED (app.mobile-otp.review-account.enabled=true) for numbers: {} "
					+ "— this is active in the '{}' profile. Confirm this is intentional (e.g. an app-store reviewer account).",
					numbers, environment.getActiveProfiles());
		}
	}

}
