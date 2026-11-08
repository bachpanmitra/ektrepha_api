package com.ektrepha.verification;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.ektrepha.model.VerificationDocType;

/**
 * Turns a nanny's set of VERIFIED {@link VerificationDocType}s into short, parent-facing badge
 * labels (nanny app spec row 25: "Parent sees 'ID verified · Police verified' badges... Parent
 * never sees the documents or numbers"). Only the types a parent would actually recognize as a
 * safety signal get a badge - EDUCATION/FIRST_AID/LIVENESS_SELFIE are internal-only.
 */
@Component
public class VerificationBadgeMapper {

	private static final Map<VerificationDocType, String> LABELS = new EnumMap<>(VerificationDocType.class);

	static {
		LABELS.put(VerificationDocType.ID_PROOF, "ID Verified");
		LABELS.put(VerificationDocType.BACKGROUND_CHECK, "Police Verified");
		LABELS.put(VerificationDocType.ADDRESS_PROOF, "Address Verified");
	}

	public List<String> toBadges(List<VerificationDocType> verifiedTypes) {
		return verifiedTypes.stream()
				.map(LABELS::get)
				.filter(label -> label != null)
				.toList();
	}

}
