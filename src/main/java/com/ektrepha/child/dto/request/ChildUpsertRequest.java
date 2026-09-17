package com.ektrepha.child.dto.request;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

public record ChildUpsertRequest(
		@NotBlank @Size(max = 100) String firstName,
		@Size(max = 100) String lastName,
		@NotNull @Past LocalDate dob,
		@Size(max = 10) String gender) {
}
