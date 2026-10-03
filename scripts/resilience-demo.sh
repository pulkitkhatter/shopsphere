#!/usr/bin/env bash
# Failure-injection demo against the running platform: shows circuit breaking, failure isolation and the outbox.
set -uo pipefail
cd "$(dirname "$0")/.."
GW=${GW:-http://localhost:8080}
ALICE_PW=${SEED_USER_PASSWORD:-Alice#Pass123}
ok()  { printf '  \033[32mPASS\033[0m %s\n' "$1"; }
bad() { printf '  \033[31mFAIL\033[0m %s\n' "$1"; FAILED=1; }
FAILED=0
code() { curl -s -o /tmp/rd-body -w '%{http_code}' -m 15 "$@"; }

TOKEN=$(curl -s -X POST "$GW/oauth/token" -d grant_type=password -d username=alice --data-urlencode "password=$ALICE_PW" | jq -r .access_token)
PID=$(curl -s "$GW/api/v2/products?category=home&size=1" | jq -r '.content[0].id')
stock() { curl -s "$GW/api/v2/products/$PID" | jq .stock; }
order() { code -X POST "$GW/api/v1/orders" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"items":[{"productId":"'$PID'","quantity":1}]}'; }

echo "== 1. Baseline"
[ "$(order)" = "201" ] && ok "order placed while everything is healthy" || bad "baseline order failed"

echo "== 2. Kill product-service (order-service depends on it for prices)"
kill "$(cat .pids/product-service.pid)"; sleep 3
START=$(date +%s)
CODES=""; for i in 1 2 3 4 5 6 7 8; do CODES="$CODES $(order)"; done
ELAPSED=$(( $(date +%s) - START ))
echo "     order attempts while product-service is down:$CODES  (${ELAPSED}s for 8 attempts)"
[ "$(echo $CODES | tr ' ' '\n' | grep -vc 503)" = "0" ] && ok "every attempt fails cleanly with 503 (no hangs, no 500s)" || bad "unexpected status codes"
[ "$ELAPSED" -lt 20 ] && ok "fails fast: the circuit breaker stops calling the dead service" || bad "too slow"
jq -e '.type=="about:blank" or .title' /tmp/rd-body >/dev/null && ok "error body is an RFC 7807 problem document: $(jq -c '{title,status}' /tmp/rd-body)" || bad "body not problem+json"
STATE=$(curl -s localhost:8083/actuator/health | jq -r '.status'); echo "     order-service health: $STATE"
[ "$(code "$GW/api/v1/notifications" -H "Authorization: Bearer $TOKEN")" = "200" ] && ok "failure isolation: notifications still work" || bad "notifications affected"
[ "$(code "$GW/api/v1/orders" -H "Authorization: Bearer $TOKEN")" = "200" ] && ok "failure isolation: reading my orders still works" || bad "order reads affected"
[ "$(code "$GW/api/v2/products")" = "503" ] && ok "gateway answers 503 problem document for the missing catalogue" || bad "gateway did not return 503"

echo "== 3. Bring product-service back"
nohup java -Xmx256m -jar product-service/target/product-service-1.0.0.jar >> logs/product-service.log 2>&1 & echo $! > .pids/product-service.pid
for i in $(seq 1 60); do [ "$(curl -s -o /dev/null -w '%{http_code}' "$GW/api/v2/products")" = "200" ] && break; sleep 2; done
[ "$(code "$GW/api/v2/products")" = "200" ] && ok "catalogue reachable again through the gateway" || bad "catalogue did not recover"
RECOVERED=no
for i in $(seq 1 30); do [ "$(order)" = "201" ] && { RECOVERED=yes; break; }; sleep 2; done
[ "$RECOVERED" = yes ] && ok "orders work again (circuit closed after the dependency recovered)" || bad "orders did not recover"

echo "== 4. Kill Kafka: orders must still be accepted (outbox), events flow once Kafka is back"
# earlier orders are applied to stock asynchronously: wait until the number stops moving before measuring
PREV=-1; for i in $(seq 1 30); do CUR=$(stock); [ "$CUR" = "$PREV" ] && break; PREV=$CUR; sleep 4; done
BEFORE=$(stock)
docker stop shopsphere-kafka >/dev/null
[ "$(order)" = "201" ] && ok "order accepted while Kafka is DOWN (event waits in the order's outbox)" || bad "order failed while Kafka down"
sleep 3
[ "$(stock)" = "$BEFORE" ] && ok "stock unchanged so far (nobody has been told yet)" || bad "stock changed without Kafka?"
docker start shopsphere-kafka >/dev/null
echo "     waiting for Kafka + consumers to recover..."
for i in $(seq 1 60); do [ "$(stock)" = "$((BEFORE-1))" ] && break; sleep 3; done
[ "$(stock)" = "$((BEFORE-1))" ] && ok "after Kafka returned the outbox delivered the event and stock dropped by 1 (exactly once)" || bad "event never delivered (stock=$(stock), expected $((BEFORE-1)))"

echo "== 5. Kill Redis (cache + rate limiter): the platform must keep serving, only slower"
docker stop shopsphere-redis >/dev/null; sleep 2
[ "$(code "$GW/api/v2/products/$PID")" = "200" ] && ok "product reads still work with the cache down (falls back to MongoDB)" || bad "reads failed without Redis"
[ "$(code -X POST "$GW/oauth/token" -d grant_type=password -d username=alice --data-urlencode "password=$ALICE_PW")" = "200" ] && ok "login still works with the rate limiter's store down (limiter fails open)" || bad "login failed without Redis"
docker start shopsphere-redis >/dev/null; sleep 3
[ "$(code "$GW/api/v2/products/$PID")" = "200" ] && ok "and everything is fine once Redis is back" || bad "no recovery after Redis restart"

echo; [ "$FAILED" = 0 ] && echo "Resilience demo: all behaviours as designed" || echo "Resilience demo: FAILURES"
exit $FAILED
