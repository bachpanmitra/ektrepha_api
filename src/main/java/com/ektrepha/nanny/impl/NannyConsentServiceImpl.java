package com.ektrepha.nanny.impl;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.exception.NannyNotFoundException;
import com.ektrepha.model.ConsentType;
import com.ektrepha.model.Nanny;
import com.ektrepha.model.NannyConsent;
import com.ektrepha.nanny.service.NannyConsentService;
import com.ektrepha.repository.NannyConsentRepository;
import com.ektrepha.repository.NannyRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NannyConsentServiceImpl implements NannyConsentService {

	private final NannyRepository nannyRepository;
	private final NannyConsentRepository nannyConsentRepository;

	@Override
	@Transactional
	public NannyConsent grant(Long userId, ConsentType type) {
		Nanny nanny = nannyRepository.findByUserId(userId)
				.orElseThrow(() -> new NannyNotFoundException("No nanny profile for the current user"));
		NannyConsent consent = nannyConsentRepository.findByNannyIdAndType(nanny.getId(), type)
				.orElseGet(() -> NannyConsent.builder().nanny(nanny).type(type).build());
		consent.setGrantedAt(Instant.now());
		return nannyConsentRepository.save(consent);
	}

	@Override
	@Transactional(readOnly = true)
	public boolean isGranted(Long nannyId, ConsentType type) {
		return nannyConsentRepository.existsByNannyIdAndType(nannyId, type);
	}

}
