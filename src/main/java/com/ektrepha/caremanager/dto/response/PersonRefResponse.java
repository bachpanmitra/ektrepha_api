package com.ektrepha.caremanager.dto.response;

/** docs/api/openapi.yaml#/components/schemas/PersonRef — id is always a public id (par_/nan_/cm_/adm_), never a raw database id. */
public record PersonRefResponse(String id, String name) {
}
