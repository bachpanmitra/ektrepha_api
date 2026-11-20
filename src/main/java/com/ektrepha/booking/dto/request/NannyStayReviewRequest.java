package com.ektrepha.booking.dto.request;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** ektrepha-nanny-ui's StayReview - stars + a fixed set of app-owned tag strings + a private note only the nanny's manager sees. */
public record NannyStayReviewRequest(
		@NotNull @Min(1) @Max(5) Short stars,
		List<String> tags,
		@Size(max = 1000) String privateNote) {
}
