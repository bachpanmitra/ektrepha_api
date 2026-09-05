#!/usr/bin/env bash
# Curl smoke test for the local Ektrepha API (dev profile).
# Mirrors docs/postman/Ektrepha-Local.postman_collection.json.
#
# Usage:
#   export DB_PASSWORD=Ektrepha JWT_SECRET=dev-only-insecure-secret-change-me-0123456789abcdef0123456789abcdef CORS_ALLOWED_ORIGINS=http://localhost:3000
#   ./mvnw spring-boot:run &
#   ./docs/postman/test-local.sh
#
# Requires: curl, jq

set -u

BASE="${BASE_URL:-http://localhost:8080}"
EMAIL="curltest+$(date +%s)@example.com"
PASSWORD="Password123!"

PASS=0
FAIL=0

check() {
  local desc="$1" expected="$2" actual="$3"
  if [ "$expected" = "$actual" ]; then
    echo "  PASS  $desc (got $actual)"
    PASS=$((PASS + 1))
  else
    echo "  FAIL  $desc (expected $expected, got $actual)"
    FAIL=$((FAIL + 1))
  fi
}

req() {
  # req METHOD PATH [BODY] [BEARER_TOKEN]
  local method="$1" path="$2" body="${3:-}" token="${4:-}"
  local args=(-s -o /tmp/ektrepha_curl_body.json -w '%{http_code}' -X "$method" "$BASE$path")
  [ -n "$body" ] && args+=(-H 'Content-Type: application/json' -d "$body")
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  curl "${args[@]}"
}

body() { cat /tmp/ektrepha_curl_body.json; }

echo "=== Health ==="
code=$(req GET /api/health)
check "GET /api/health" 200 "$code"
[ "$(body | jq -r .status)" = "UP" ] && echo "  PASS  status field is UP" && PASS=$((PASS+1)) || { echo "  FAIL  status field is UP"; FAIL=$((FAIL+1)); }

code=$(req GET /api/version)
check "GET /api/version" 200 "$code"

echo ""
echo "=== Auth ==="
code=$(req POST /api/v1/auth/register "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\",\"name\":\"Curl Test Parent\",\"phoneNumber\":\"9999999999\",\"role\":\"PARENT\"}")
check "Register Parent" 201 "$code"

code=$(req POST /api/v1/auth/register "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\",\"name\":\"Dup\",\"phoneNumber\":\"9999999998\",\"role\":\"PARENT\"}")
check "Register Duplicate Email" 409 "$code"

code=$(req POST /api/v1/auth/register "{\"email\":\"wannabe-admin+$(date +%s)@example.com\",\"password\":\"$PASSWORD\",\"name\":\"Nope\",\"phoneNumber\":\"9999999997\",\"role\":\"ADMIN\"}")
check "Register as ADMIN (blocked)" 400 "$code"

code=$(req POST /api/v1/auth/login/email "{\"email\":\"$EMAIL\",\"password\":\"wrong-password\"}")
check "Login wrong password" 401 "$code"

code=$(req POST /api/v1/auth/login/email "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")
check "Login" 200 "$code"
ACCESS_TOKEN=$(body | jq -r .accessToken)
REFRESH_TOKEN=$(body | jq -r .refreshToken)
if [ -n "$ACCESS_TOKEN" ] && [ "$ACCESS_TOKEN" != "null" ]; then
  echo "  PASS  received access + refresh token"
  PASS=$((PASS+1))
else
  echo "  FAIL  received access + refresh token"
  FAIL=$((FAIL+1))
fi

code=$(req POST /api/v1/auth/refresh "{\"refreshToken\":\"$REFRESH_TOKEN\"}")
check "Refresh Token" 200 "$code"
ACCESS_TOKEN=$(body | jq -r .accessToken)
REFRESH_TOKEN=$(body | jq -r .refreshToken)

code=$(req POST /api/v1/auth/password/forgot "{\"email\":\"$EMAIL\"}")
check "Forgot Password" 200 "$code"

echo "  ----  Reset Password skipped: the reset OTP is only in the app's log"
echo "        (EmailService stub logs it, doesn't send real email). To test it:"
echo "        grep 'OTP for' <app log>, then:"
echo "        curl -X POST $BASE/api/v1/auth/password/reset -H 'Content-Type: application/json' \\"
echo "             -d '{\"phoneOrEmail\":\"$EMAIL\",\"otp\":\"<paste>\",\"newPassword\":\"NewPassword456!\"}'"

code=$(req POST /api/v1/auth/logout "{\"refreshToken\":\"$REFRESH_TOKEN\"}")
check "Logout" 200 "$code"

echo ""
echo "=== RBAC Demo ==="
# Re-login since the token above was just logged out.
code=$(req POST /api/v1/auth/login/email "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")
ACCESS_TOKEN=$(body | jq -r .accessToken)

code=$(req POST /api/v1/bookings)
check "Create Booking, no token" 401 "$code"

code=$(req POST /api/v1/bookings "" "$ACCESS_TOKEN")
check "Create Booking, as PARENT" 201 "$code"

code=$(req GET /api/v1/admin/anything "" "$ACCESS_TOKEN")
check "Admin Endpoint, as PARENT" 403 "$code"

echo ""
echo "=== Nanny Search & Reviews ==="
# No endpoint exists yet to create a Parent/Nanny profile row (separate,
# unstarted work) — a registered user only has a users row. So the
# valid-shape search/review checks below expect 404/400 (no parent profile /
# no eligible booking), not a real result. Insert parent/nanny/booking rows
# via psql first if you want to see actual ranked results or a real review.

code=$(req GET /api/v1/nanny-search/languages)
check "Languages, no token" 401 "$code"

code=$(req GET /api/v1/nanny-search/languages "" "$ACCESS_TOKEN")
check "Languages" 200 "$code"

code=$(req GET /api/v1/nanny-search/skills "" "$ACCESS_TOKEN")
check "Skills" 200 "$code"

code=$(req POST /api/v1/nanny-search "{\"radiusKm\":10,\"windowStart\":\"2026-09-10T10:00:00Z\",\"windowEnd\":\"2026-09-10T12:00:00Z\",\"childId\":1}")
check "Search, no token" 401 "$code"

code=$(req POST /api/v1/nanny-search "{\"radiusKm\":7,\"windowStart\":\"2026-09-10T10:00:00Z\",\"windowEnd\":\"2026-09-10T12:00:00Z\",\"childId\":1}" "$ACCESS_TOKEN")
check "Search, invalid radius" 400 "$code"

code=$(req POST /api/v1/nanny-search "{\"radiusKm\":10,\"windowStart\":\"2026-09-10T10:00:00Z\",\"windowEnd\":\"2026-09-10T12:00:00Z\",\"childId\":1}" "$ACCESS_TOKEN")
if [ "$code" = "200" ] || [ "$code" = "404" ]; then
  echo "  PASS  Search, valid shape (got $code)"
  PASS=$((PASS+1))
else
  echo "  FAIL  Search, valid shape (expected 200 or 404, got $code)"
  FAIL=$((FAIL+1))
fi

code=$(req POST /api/v1/reviews "{\"bookingId\":1,\"rating\":6}" "$ACCESS_TOKEN")
check "Submit Review, invalid rating" 400 "$code"

code=$(req POST /api/v1/reviews "{\"bookingId\":1,\"rating\":5,\"comment\":\"Great!\"}" "$ACCESS_TOKEN")
if [ "$code" = "201" ] || [ "$code" = "400" ] || [ "$code" = "404" ]; then
  echo "  PASS  Submit Review, valid shape (got $code)"
  PASS=$((PASS+1))
else
  echo "  FAIL  Submit Review, valid shape (expected 201, 400, or 404, got $code)"
  FAIL=$((FAIL+1))
fi

echo ""
echo "=== Summary: $PASS passed, $FAIL failed ==="
rm -f /tmp/ektrepha_curl_body.json
[ "$FAIL" -eq 0 ] && exit 0 || exit 1
