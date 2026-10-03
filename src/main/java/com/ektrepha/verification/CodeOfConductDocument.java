package com.ektrepha.verification;

/**
 * The code of conduct every caregiver must accept before being eligible for APPROVED status.
 * TODO(legal): placeholder copy - needs a lawyer's review before launch (same TODO as the rest of
 * the child-safety program's policy text).
 */
public final class CodeOfConductDocument {

	public static final String CURRENT_VERSION = "v1";

	public static final String TEXT = """
			Ektrepha Caregiver Code of Conduct (%s)

			1. The child's safety and wellbeing come before convenience, speed, or payment at all times.
			2. Never administer any medicine, food, or treatment not explicitly listed on the child's care notes without first confirming with the parent through Ektrepha.
			3. Physical contact must stay appropriate and visible - no closed-door, one-on-one physical contact beyond what basic care requires.
			4. Never photograph, record, or share a child's image or information outside of what the parent has explicitly shared through the Ektrepha app for the current booking.
			5. In a medical or safety emergency, call 112 (national emergency) immediately, then notify the parent and Ektrepha support.
			6. If you witness or suspect abuse or neglect of any kind, you must report it to Ektrepha support immediately. Depending on the circumstances, Indian law (the POCSO Act, 2012) may require reporting to the police or the Special Juvenile Police Unit - Ektrepha will support you through that process.
			7. Never leave a child unattended, and never bring another person to a booking without the parent's explicit prior consent.
			8. Breach of this code of conduct can result in suspension or a permanent ban from the platform.
			""".formatted(CURRENT_VERSION);

	private CodeOfConductDocument() {
	}

}
