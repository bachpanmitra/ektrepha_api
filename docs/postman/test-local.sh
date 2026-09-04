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
code=$(req POST /api/v1/auth/register "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\",\"fullName\":\"Curl Test Parent\",\"phoneNumber\":\"9999999999\",\"role\":\"PARENT\"}")
check "Register Parent" 201 "$code"

code=$(req POST /api/v1/auth/register "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\",\"fullName\":\"Dup\",\"role\":\"PARENT\"}")
check "Register Duplicate Email" 409 "$code"

code=$(req POST /api/v1/auth/register "{\"email\":\"wannabe-admin+$(date +%s)@example.com\",\"password\":\"$PASSWORD\",\"fullName\":\"Nope\",\"role\":\"ADMIN\"}")
check "Register as ADMIN (blocked)" 400 "$code"

code=$(req POST /api/v1/auth/login "{\"email\":\"$EMAIL\",\"password\":\"wrong-password\"}")
check "Login wrong password" 401 "$code"

code=$(req POST /api/v1/auth/login "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")
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

code=$(req POST /api/v1/auth/forgot-password "{\"email\":\"$EMAIL\"}")
check "Forgot Password" 200 "$code"

echo "  ----  Reset Password skipped: the reset token is only in the app's log"
echo "        (EmailService stub logs it, doesn't send real email). To test it:"
echo "        grep 'Password reset requested' <app log>, then:"
echo "        curl -X POST $BASE/api/v1/auth/reset-password -H 'Content-Type: application/json' \\"
echo "             -d '{\"token\":\"<paste>\",\"newPassword\":\"NewPassword456!\"}'"

code=$(req POST /api/v1/auth/logout "{\"refreshToken\":\"$REFRESH_TOKEN\"}")
check "Logout" 204 "$code"

echo ""
echo "=== RBAC Demo ==="
# Re-login since the token above was just logged out.
code=$(req POST /api/v1/auth/login "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")
ACCESS_TOKEN=$(body | jq -r .accessToken)

code=$(req POST /api/v1/bookings)
check "Create Booking, no token" 401 "$code"

code=$(req POST /api/v1/bookings "" "$ACCESS_TOKEN")
check "Create Booking, as PARENT" 201 "$code"

code=$(req GET /api/v1/admin/anything "" "$ACCESS_TOKEN")
check "Admin Endpoint, as PARENT" 403 "$code"

echo ""
echo "=== Summary: $PASS passed, $FAIL failed ==="
rm -f /tmp/ektrepha_curl_body.json
[ "$FAIL" -eq 0 ] && exit 0 || exit 1
