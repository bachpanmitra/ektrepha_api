# ADR 0001: API path structure and reverse proxy

## Status
Accepted

## Decision
All application endpoints are versioned under `/api/v1/...` from the first
endpoint onward (e.g. the health check is `/api/v1/health`, not `/api/health`).
Actuator stays unversioned at `/actuator/...` since it's operational, not
part of the public API contract.

In front of the app, use a plain reverse proxy — Nginx locally and on
whatever host we deploy to first — terminating TLS and forwarding to the
Spring Boot app on its internal port. This is deliberately the simplest
option, not a real API gateway (no request transformation, no
auth-at-the-edge, no rate limiting yet).

## Why now
Changing a path prefix after clients exist is a breaking change. Deciding
`/api/v1` before the second endpoint exists costs nothing; deciding it
after the fifth endpoint costs a migration.

## Why Nginx over AWS ALB, for now
No AWS account is provisioned yet for this project. Nginx runs anywhere
(laptop, any VM, any container host) and validates the path/proxy
structure without taking a dependency on AWS. If and when this deploys to
AWS, swapping Nginx for an ALB is a routing change, not an application
change — the app never talks to the proxy layer directly, so nothing in
the codebase needs to know which one is in front of it.

## Revisit when
- An AWS account exists and we're choosing real deployment infrastructure
- We need edge concerns Nginx doesn't give us for free: auth at the edge,
  per-route rate limiting, request/response transformation
