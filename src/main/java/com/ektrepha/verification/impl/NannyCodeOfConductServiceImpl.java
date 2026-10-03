package com.ektrepha.verification.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.exception.NannyNotFoundException;
import com.ektrepha.model.Nanny;
import com.ektrepha.model.NannyCodeOfConductAcceptance;
import com.ektrepha.repository.NannyCodeOfConductAcceptanceRepository;
import com.ektrepha.repository.NannyRepository;
import com.ektrepha.verification.CodeOfConductDocument;
import com.ektrepha.verification.service.NannyCodeOfConductService;
import com.ektrepha.verification.service.NannyVerificationService;

import lombok.RequiredArgsConstructor;

/** PRD: "Signed code of conduct (stored with timestamp and version)." Every acceptance is a new row (see {@code NannyCodeOfConductAcceptance} javadoc) - the version itself is never client-supplied, always {@link CodeOfConductDocument#CURRENT_VERSION}. */
@Service
@RequiredArgsConstructor
public class NannyCodeOfConductServiceImpl implements NannyCodeOfConductService {

	private final NannyCodeOfConductAcceptanceRepository nannyCodeOfConductAcceptanceRepository;
	private final NannyRepository nannyRepository;
	private final NannyVerificationService nannyVerificationService;

	@Override
	@Transactional
	public NannyCodeOfConductAcceptance accept(Long userId, String ipAddress) {
		Nanny nanny = nannyRepository.findByUserId(userId)
				.orElseThrow(() -> new NannyNotFoundException("No nanny profile for the current user"));

		NannyCodeOfConductAcceptance acceptance = nannyCodeOfConductAcceptanceRepository.save(NannyCodeOfConductAcceptance.builder()
				.nanny(nanny)
				.version(CodeOfConductDocument.CURRENT_VERSION)
				.acceptedAt(Instant.now())
				.ipAddress(ipAddress)
				.build());

		nannyVerificationService.recompute(nanny.getId());
		return acceptance;
	}

	@Override
	@Transactional(readOnly = true)
	public boolean hasAcceptedCurrentVersion(Long nannyId) {
		return nannyCodeOfConductAcceptanceRepository.existsByNannyIdAndVersion(nannyId, CodeOfConductDocument.CURRENT_VERSION);
	}

	@Override
	@Transactional(readOnly = true)
	public List<NannyCodeOfConductAcceptance> history(Long nannyId) {
		return nannyCodeOfConductAcceptanceRepository.findByNannyIdOrderByCreatedAtDesc(nannyId);
	}

}
