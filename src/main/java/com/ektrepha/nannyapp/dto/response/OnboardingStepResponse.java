package com.ektrepha.nannyapp.dto.response;

/** One joining-checklist row. id: OFFER_LETTER | ID_ADDRESS_PROOF | POLICE_VERIFICATION | BANK_ACCOUNT | PF_ESI | TRAINING | UNIFORM_ID_CARD; status: DONE | IN_REVIEW | TODO. */
public record OnboardingStepResponse(
		String id, String status, String detail) {
}
