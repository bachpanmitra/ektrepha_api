package com.ektrepha.admin.dto.request;

import com.ektrepha.model.BookingCancellationReasonCode;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Ops override-cancel — unlike the parent-facing {@code BookingCancelRequest}, this can be applied
 * to a booking in any non-terminal status (including IN_PROGRESS), so a reason is mandatory: it
 * overrides whatever the parent/nanny already committed to, and needs to leave a record of why.
 * {@code reasonCode} backs the admin UI's reason dropdown and is what
 * {@code booking_cancellation_log}/ops reporting actually groups by - {@code reason} is the
 * free-text detail shown alongside it (and the only thing that matters when the code is
 * {@link BookingCancellationReasonCode#OTHER}). */
public record AdminBookingCancelRequest(
		@Schema(description = "Reportable reason category - backs the admin UI's dropdown") @NotNull BookingCancellationReasonCode reasonCode,
		@Schema(description = "Why ops is cancelling this booking", example = "Parent requested cancellation by phone, past the self-serve window")
		@NotBlank String reason) {
}
