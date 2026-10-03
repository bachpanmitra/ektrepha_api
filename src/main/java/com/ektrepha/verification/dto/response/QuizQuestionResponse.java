package com.ektrepha.verification.dto.response;

import java.util.List;

/** Never carries the correct answer - see {@code ChildSafetyTrainingQuiz}. */
public record QuizQuestionResponse(String id, String topic, String prompt, List<String> options) {
}
