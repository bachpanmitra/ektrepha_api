package com.ektrepha.banner.dto.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;

/** orderedIds is the full new front-to-back order — every live banner's id, once each. */
public record ReorderBannersRequest(@NotEmpty List<UUID> orderedIds) {
}
