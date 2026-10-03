#!/usr/bin/env bash
# End-to-end test against the running platform (start it with scripts/start-all.sh). Everything goes through the gateway.
set -uo pipefail
GW=${GW:-http://localhost:8080}
ADMIN_PW=${SEED_ADMIN_PASSWORD:-Admin#Pass123}
ALICE_PW=${SEED_USER_PASSWORD:-Alice#Pass123}
PASS=0; FAIL=0

ok()   { PASS=$((PASS+1)); printf '  \033[32mPASS\033[0m %s\n' "$1"; }
bad()  { FAIL=$((FAIL+1)); printf '  \033[31mFAIL\033[0m %s\n         %s\n' "$1" "${2:-}"; }
check(){ if [ "$2" = "$3" ]; then ok "$1"; else bad "$1" "expected '$3', got '$2'"; fi; }

# call METHOD PATH [curl args...]  -> sets STATUS, BODY, HEADERS
call() {
  local method=$1 path=$2; shift 2
  # the login/registration endpoints are rate limited per IP (burst 10, 2/s): pace ourselves like a polite client
  case "$path" in /oauth/token|/auth/register) sleep 0.7;; esac
  local out; out=$(curl -s -m 15 -D /tmp/ss-headers -o /tmp/ss-body -w '%{http_code}' -X "$method" "$GW$path" "$@")
  STATUS=$out; BODY=$(cat /tmp/ss-body); HEADERS=$(cat /tmp/ss-headers)
}
token() { # user pass -> prints access token; the refresh token is left in /tmp/ss-refresh (a $(...) subshell cannot set variables)
  call POST /oauth/token -d grant_type=password --data-urlencode "username=$1" --data-urlencode "password=$2"
  echo "$BODY" | jq -r .refresh_token > /tmp/ss-refresh; echo "$BODY" | jq -r .access_token
}
auth() { echo "Authorization: Bearer $1"; }

echo "== Waiting for the login rate limiter to refill (a previous run may have exhausted it)"
for i in $(seq 1 30); do
  [ "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$GW/oauth/token" -d grant_type=password -d username=x -d password=y)" != "429" ] && break; sleep 2
done
sleep 6

echo "== Platform"
call GET /actuator/health; check "gateway healthy" "$STATUS" 200
# a freshly started gateway needs a few seconds before it has seen every service in the Eureka registry
for i in $(seq 1 45); do
  A=$(curl -s -o /dev/null -w '%{http_code}' "$GW/api/v2/products"); B=$(curl -s -o /dev/null -w '%{http_code}' "$GW/.well-known/jwks.json")
  [ "$A$B" = "200200" ] && break; sleep 2
done

echo "== Public catalogue (v2)"
call GET "/api/v2/products?size=3&sort=price,asc"; check "list is public" "$STATUS" 200
check "page envelope has totals" "$(echo "$BODY" | jq '.totalElements > 0')" true
check "price sorted numerically (first is cheapest)" "$(echo "$BODY" | jq '.content[0].price.amount <= .content[1].price.amount')" true
call GET "/api/v2/products?q=laptop&sort=price,asc"; check "full-text search finds laptops" "$(echo "$BODY" | jq '[.content[].name] | map(test("Laptop")) | all')" true
call GET "/api/v2/products?category=books&inStock=true"; check "filters combine (books in stock)" "$(echo "$BODY" | jq '[.content[].inStock] | all')" true
call GET "/api/v2/products?size=1000"; check "oversized page rejected" "$STATUS" 400
call GET "/api/v2/products?sort=passwordHash,asc"; check "sorting on arbitrary field rejected" "$STATUS" 400
call GET "/api/v1/products"; check "v1 still works" "$STATUS" 200
check "v1 announces deprecation" "$(echo "$HEADERS" | grep -ci '^deprecation: true')" 1
check "v1 announces sunset date" "$(echo "$HEADERS" | grep -ci '^sunset:')" 1

echo "== Security"
call POST /api/v2/products -H 'Content-Type: application/json' -d '{}'; check "create product without token -> 401" "$STATUS" 401
call GET /api/v1/orders; check "orders without token -> 401" "$STATUS" 401
call GET /api/v1/orders -H "Authorization: Bearer not.a.jwt"; check "garbage token -> 401" "$STATUS" 401
call GET /actuator/env; check "actuator env not exposed -> 401" "$STATUS" 401
call POST /oauth/token -d grant_type=password -d username=alice -d password=wrong; check "wrong password -> 400" "$STATUS" 400
check "RFC6749 error body" "$(echo "$BODY" | jq -r .error)" invalid_grant
call POST /oauth/token -d grant_type=password -d username=nobody -d password=wrongwrong; check "unknown user gives same answer" "$(echo "$BODY" | jq -r .error_description)" "Bad credentials"
call GET /.well-known/jwks.json; check "JWKS published" "$(echo "$BODY" | jq -r '.keys[0].alg')" RS256
check "JWKS exposes no private key" "$(echo "$BODY" | jq '.keys[0] | has("d")')" false
call GET /.well-known/openid-configuration; check "OIDC discovery" "$(echo "$BODY" | jq -r .token_endpoint | grep -c oauth/token)" 1

ADMIN=$(token admin "$ADMIN_PW"); ALICE=$(token alice "$ALICE_PW")
check "admin can log in" "$([ ${#ADMIN} -gt 100 ] && echo yes)" yes
SUFFIX=$RANDOM
NEWSKU="SMK-$SUFFIX"
PRODUCT='{"sku":"'$NEWSKU'","name":"Smoke Widget","description":"made by smoke test","category":"smoke","price":19.99,"stock":10,"tags":["smoke"]}'
call POST /api/v2/products -H "$(auth "$ALICE")" -H 'Content-Type: application/json' -d "$PRODUCT"; check "customer cannot create product -> 403" "$STATUS" 403
call POST /api/v2/products -H "$(auth "$ADMIN")" -H 'Content-Type: application/json' -d "$PRODUCT"; check "admin creates product -> 201" "$STATUS" 201
PID=$(echo "$BODY" | jq -r .id)
call POST /api/v2/products -H "$(auth "$ADMIN")" -H 'Content-Type: application/json' -d "$PRODUCT"; check "duplicate SKU -> 409" "$STATUS" 409
call POST /api/v2/products -H "$(auth "$ADMIN")" -H 'Content-Type: application/json' -d '{"sku":"bad sku","name":"","category":"x","price":-1,"stock":-1}'
check "invalid product -> 400 with field errors" "$STATUS $(echo "$BODY" | jq '.errors | has("sku") and has("price")')" "400 true"

echo "== Registration, refresh-token rotation"
USER="smoke$SUFFIX"
call POST /auth/register -H 'Content-Type: application/json' -d '{"username":"'$USER'","email":"'$USER'@example.com","password":"short"}'; check "weak password rejected" "$STATUS" 400
call POST /auth/register -H 'Content-Type: application/json' -d '{"username":"'$USER'","email":"'$USER'@example.com","password":"a-long-enough-pass"}'; check "register -> 201" "$STATUS" 201
check "self-registration yields only USER role" "$(echo "$BODY" | jq -c .roles)" '["USER"]'
call POST /auth/register -H 'Content-Type: application/json' -d '{"username":"'$USER'","email":"'$USER'@example.com","password":"a-long-enough-pass"}'; check "duplicate registration -> 409" "$STATUS" 409
BOB=$(token "$USER" "a-long-enough-pass"); R1=$(cat /tmp/ss-refresh)
call POST /oauth/token -d grant_type=refresh_token -d refresh_token="$R1"; check "refresh token works once" "$STATUS" 200
call POST /oauth/token -d grant_type=refresh_token -d refresh_token="$R1"; check "reused refresh token rejected (rotation)" "$STATUS" 400
call GET /userinfo -H "$(auth "$BOB")"; check "userinfo returns subject" "$(echo "$BODY" | jq -r .sub)" "$USER"

echo "== Orders, idempotency, Kafka (stock + notifications)"
stock() { curl -s "$GW/api/v2/products/$PID" | jq .stock; }
check "initial stock" "$(stock)" 10
KEY="smoke-key-$SUFFIX-1"
ORDER='{"items":[{"productId":"'$PID'","quantity":3}]}'
call POST /api/v1/orders -H "$(auth "$ALICE")" -H "Idempotency-Key: $KEY" -H 'Content-Type: application/json' -d "$ORDER"; check "place order -> 201" "$STATUS" 201
OID=$(echo "$BODY" | jq -r .id); check "total = 3 x 19.99" "$(echo "$BODY" | jq .total)" 59.97
call POST /api/v1/orders -H "$(auth "$ALICE")" -H "Idempotency-Key: $KEY" -H 'Content-Type: application/json' -d "$ORDER"
check "retry with same Idempotency-Key -> 200, same order" "$STATUS $(echo "$BODY" | jq -r .id)" "200 $OID"
call POST /api/v1/orders -H "$(auth "$ALICE")" -H 'Content-Type: application/json' -d '{"items":[{"productId":"'$PID'","quantity":1,"price":0.01}]}'
check "client-supplied price is ignored (total from catalogue)" "$(echo "$BODY" | jq .total)" 19.99
O2=$(echo "$BODY" | jq -r .id)

for i in $(seq 1 20); do [ "$(stock)" = "6" ] && break; sleep 0.5; done
check "stock decremented via Kafka (10 - 3 - 1, retry did not double count)" "$(stock)" 6

for i in $(seq 1 20); do call GET /api/v1/notifications -H "$(auth "$ALICE")"; [ "$(echo "$BODY" | jq '[.content[] | select(.orderId=="'$OID'")] | length')" = "1" ] && break; sleep 0.5; done
check "notification created from order event" "$(echo "$BODY" | jq '[.content[] | select(.orderId=="'$OID'")] | length')" 1

call POST /api/v1/orders -H "$(auth "$ALICE")" -H 'Content-Type: application/json' -d '{"items":[{"productId":"'$PID'","quantity":50}]}'; check "insufficient stock -> 422" "$STATUS" 422
call POST /api/v1/orders -H "$(auth "$ALICE")" -H 'Content-Type: application/json' -d '{"items":[{"productId":"doesnotexist1","quantity":1}]}'; check "unknown product -> 422" "$STATUS" 422
call POST /api/v1/orders -H "$(auth "$ALICE")" -H 'Content-Type: application/json' -d '{"items":[]}'; check "empty order -> 400" "$STATUS" 400

call GET "/api/v1/orders/$OID" -H "$(auth "$BOB")"; check "other user cannot read my order (404, not 403)" "$STATUS" 404
call POST "/api/v1/orders/$OID/cancel" -H "$(auth "$BOB")"; check "other user cannot cancel my order" "$STATUS" 404
call GET "/api/v1/orders/$OID" -H "$(auth "$ADMIN")"; check "admin can read any order" "$STATUS" 200
call GET /api/v1/orders/all -H "$(auth "$ALICE")"; check "order list of everyone is admin only" "$STATUS" 403
call GET /api/v1/orders -H "$(auth "$ALICE")"; check "my orders contain mine" "$(echo "$BODY" | jq '[.content[].id] | index("'$OID'") != null')" true

call POST "/api/v1/orders/$OID/cancel" -H "$(auth "$ALICE")" -H 'X-Correlation-Id: smoke-trace-0001'; check "cancel -> 200 CANCELLED" "$STATUS $(echo "$BODY" | jq -r .status)" "200 CANCELLED"
call POST "/api/v1/orders/$OID/cancel" -H "$(auth "$ALICE")"; check "cancelling twice -> 409" "$STATUS" 409
for i in $(seq 1 20); do [ "$(stock)" = "9" ] && break; sleep 0.5; done
check "stock restored via Kafka after cancel (6 + 3)" "$(stock)" 9

echo "== Product update + cache invalidation"
CUR=$(curl -s "$GW/api/v2/products/$PID")                 # primes the Redis cache
UPD='{"sku":"'$NEWSKU'","name":"Smoke Widget Pro","description":"x","category":"smoke","price":24.50,"stock":9,"tags":["smoke"]}'
call PUT "/api/v2/products/$PID" -H "$(auth "$ADMIN")" -H 'Content-Type: application/json' -d "$UPD"; check "admin updates product" "$STATUS" 200
check "cache evicted: next read shows the new price" "$(curl -s "$GW/api/v2/products/$PID" | jq '.price.amount == 24.5')" true
call DELETE "/api/v2/products/$PID" -H "$(auth "$ADMIN")"; check "admin deletes product -> 204" "$STATUS" 204
call GET "/api/v2/products/$PID"; check "deleted product -> 404 problem+json" "$STATUS $(echo "$BODY" | jq -r .title)" "404 Resource not found"

echo "== Observability"
call GET /api/v2/products -H 'X-Correlation-Id: smoke-trace-0002'; check "correlation id echoed exactly once" "$(echo "$HEADERS" | grep -ci 'x-correlation-id: smoke-trace-0002')" 1
check "correlation id travelled gateway -> order-service log line" "$(grep -c 'smoke-trace-0001' logs/order-service.log 2>/dev/null | awk '{print ($1>0)?"yes":"no"}')" yes
check "Prometheus metrics exposed by product-service" "$(curl -s localhost:8082/actuator/prometheus | grep -c '^http_server_requests_seconds_count' | awk '{print ($1>0)?"yes":"no"}')" yes
check "cache metrics show hits (Redis read-through cache works)" "$(curl -s localhost:8082/actuator/prometheus | grep '^cache_gets_total' | grep -c 'result="hit"' | awk '{print ($1>0)?"yes":"no"}')" yes

echo "== Rate limiting (login is limited per IP: burst 10, 2/s)"
# an attacker is concurrent: fire 40 wrong-password logins in parallel
CODES=$(seq 1 40 | xargs -P 20 -I{} curl -s -o /dev/null -w '%{http_code}\n' -X POST "$GW/oauth/token" -d grant_type=password -d username=x -d password=y)
check "excess parallel login attempts get 429" "$(echo "$CODES" | grep -c 429 | awk '{print ($1>0)?"yes":"no"}')" yes
check "...but the first ones were still served (burst allowance)" "$(echo "$CODES" | grep -c 400 | awk '{print ($1>=5)?"yes":"no"}')" yes

echo
printf 'Result: %d passed, %d failed\n' "$PASS" "$FAIL"
[ "$FAIL" -eq 0 ]
