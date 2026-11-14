package com.ektrepha.caremanager.dto.request;

import jakarta.validation.constraints.Size;

/** The {reason?} body shared by POST /me/care-manager/change-requests and POST /bookings/{id}/nanny-change-requests. */
public record ReasonRequest(@Size(max = 500) String reason) {
}
