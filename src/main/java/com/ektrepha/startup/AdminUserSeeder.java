package com.ektrepha.startup;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.ektrepha.model.User;
import com.ektrepha.model.UserSource;
import com.ektrepha.model.UserType;
import com.ektrepha.repository.UserRepository;
import com.ektrepha.config.properties.AppProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Seeds a default ADMIN user so there's a way into the platform before any
 * real admin exists. AuthService.register() deliberately refuses to create
 * ADMIN accounts, so this is the intended bootstrap path — never a public
 * endpoint. Double-guarded: only wired up in dev/stage at all, and still
 * requires app.startup.seed-admin=true plus explicit credentials.
 */
@Slf4j
@Component
@Order(3)
@Profile({ "dev", "stage" })
@RequiredArgsConstructor
public class AdminUserSeeder implements ApplicationRunner {

	private final AppProperties appProperties;
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	@Override
	public void run(ApplicationArguments args) {
		if (!appProperties.startup().seedAdmin()) {
			log.info("Admin user seeding skipped (app.startup.seed-admin=false)");
			return;
		}

		String email = appProperties.startup().seedAdminEmail();
		String password = appProperties.startup().seedAdminPassword();

		if (email == null || email.isBlank() || password == null || password.isBlank()) {
			log.warn("app.startup.seed-admin is true but seed-admin-email/seed-admin-password are not set; skipping.");
			return;
		}

		if (userRepository.existsByEmail(email)) {
			log.info("Seed admin user {} already exists, skipping.", email);
			return;
		}

		User admin = User.builder()
				.email(email)
				.password(passwordEncoder.encode(password))
				.userType(UserType.ADMIN)
				.userSource(UserSource.EMAIL)
				.name("Seed Admin")
				.emailVerified(true)
				.active(true)
				.build();
		userRepository.save(admin);
		log.info("Seeded default ADMIN user: {}", email);
	}

}
