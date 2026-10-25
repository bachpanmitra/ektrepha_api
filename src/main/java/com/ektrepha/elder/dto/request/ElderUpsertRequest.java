package com.ektrepha.elder.dto.request;

import java.util.List;

import com.ektrepha.elder.dto.ElderCarePreferences;
import com.ektrepha.model.ElderMobility;
import com.ektrepha.model.ElderRelationship;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ElderUpsertRequest(
		@NotBlank @Size(max = 100) String firstName,
		@Size(max = 100) String lastName,
		@NotNull @Min(40) @Max(120) Integer age,
		@Size(max = 10) String gender,
		ElderRelationship relationship,
		@NotNull ElderMobility mobility,
		List<String> conditions,
		List<String> allergies,
		@Size(max = 500) String medicationNotes,
		@Size(max = 30) String preferredLanguage,
		@Size(max = 100) String emergencyContactName,
		@Size(max = 20) String emergencyContactPhone,
		@Size(max = 100) String doctorName,
		@Size(max = 20) String doctorPhone,
		ElderCarePreferences carePreferences) {
}
