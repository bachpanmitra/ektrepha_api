package com.ektrepha.verification.training;

import java.util.List;

public record QuizQuestion(String id, String topic, String prompt, List<String> options, int correctOptionIndex) {
}
