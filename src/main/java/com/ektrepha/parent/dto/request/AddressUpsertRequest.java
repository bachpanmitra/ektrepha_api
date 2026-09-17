package com.ektrepha.parent.dto.request;

import com.ektrepha.model.AddressLabel;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressUpsertRequest(
		@NotNull AddressLabel label,
		@NotBlank @Size(max = 255) String addressLine1,
		@Size(max = 255) String addressLine2,
		@Size(max = 255) String landmark,
		@Size(max = 255) String accessNotes,
		@NotBlank @Pattern(regexp = "\\d{6}", message = "must be a 6-digit pincode") String pincode,
		@NotBlank @Size(max = 100) String city,
		@NotBlank @Size(max = 100) String state,
		Double lat,
		Double lng,
		// Boxed, not a primitive: a record component with no value in the request body fails
		// deserialization as a primitive on this app's Jackson setup instead of defaulting to
		// false, and "make this my primary address" is legitimately absent on most requests.
		Boolean makePrimary) {

	public boolean shouldBePrimary() {
		return Boolean.TRUE.equals(makePrimary);
	}
}
