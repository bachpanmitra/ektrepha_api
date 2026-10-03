package com.ektrepha.verification.training;

import java.util.List;

/**
 * The mandatory child-safety training module's quiz (PRD: "code of conduct, safe touch
 * boundaries, emergencies, reporting duties, photo/privacy rules. Must pass."). Graded
 * server-side by {@code NannyVerificationServiceImpl#submitTrainingAttempt} - the client only
 * ever sees question text/options, never {@link QuizQuestion#correctOptionIndex}.
 * <p>
 * TODO(legal): this content (and the pass threshold) needs a lawyer's review before launch, per
 * the POCSO/DPDP compliance TODO on the broader child-safety program - it is a reasonable
 * placeholder, not reviewed legal copy.
 */
public final class ChildSafetyTrainingQuiz {

	public static final String MODULE_VERSION = "v1";
	public static final int PASS_THRESHOLD_PERCENT = 80;

	public static final List<QuizQuestion> QUESTIONS = List.of(
			new QuizQuestion(
					"code-of-conduct",
					"Code of conduct",
					"A parent asks you to give their child medicine not listed on the child's care notes. What should you do?",
					List.of(
							"Give it anyway, the parent asked in person",
							"Decline and tell the parent to update the child's care notes with Ektrepha before any medicine outside what's on file is given",
							"Give it but don't tell anyone",
							"Give half the dose to be safe"),
					1),
			new QuizQuestion(
					"safe-touch",
					"Safe touch boundaries",
					"Which of these is an appropriate way to comfort a distressed toddler?",
					List.of(
							"Any form of physical affection is fine in private",
							"A side hug or hand-hold in view of other adults/cameras, never in a closed room alone",
							"Physical comfort should always happen with the door closed for the child's privacy",
							"Avoid any physical contact even when the child is hurt"),
					1),
			new QuizQuestion(
					"emergencies",
					"Emergencies",
					"A child in your care is unresponsive. What is the correct first step?",
					List.of(
							"Wait for the parent to come home before doing anything",
							"Post in a parenting forum for advice",
							"Call 112 (national emergency) immediately, then notify the parent and Ektrepha support",
							"Try home remedies first"),
					2),
			new QuizQuestion(
					"reporting-duty",
					"Reporting duties",
					"You suspect a child may be experiencing abuse at home. What must you do?",
					List.of(
							"Nothing - it's a family matter",
							"Confront the family directly yourself",
							"Report it to Ektrepha support and, where required by law (POCSO), to the police or Special Juvenile Police Unit - never stay silent",
							"Only mention it if the parent asks"),
					2),
			new QuizQuestion(
					"photo-privacy",
					"Photo/privacy rules",
					"Can you post a photo of a child you're caring for on your personal social media?",
					List.of(
							"Yes, if the photo is cute",
							"Yes, with a filter so the face is blurred",
							"No - a child's photo may only be shared with their own parent, through Ektrepha, with consent, and never posted publicly",
							"Yes, if you delete it after 24 hours"),
					2));

	private ChildSafetyTrainingQuiz() {
	}

}
