#!/usr/bin/env bash
# Builds and starts the whole platform: infra (Docker) + 6 Spring services + the web front end.
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p logs .pids

# rebuilding jars under a running JVM breaks it (classes are loaded lazily from the jar), so refuse to do that
for f in .pids/*.pid; do
  [ -e "$f" ] && kill -0 "$(cat "$f")" 2>/dev/null && { echo "Services are already running. Run scripts/stop-all.sh first (or scripts/smoke-test.sh to test them)."; exit 1; }
done

echo "==> Starting MongoDB, Kafka and Redis (docker compose)"
docker compose up -d
until [ "$(docker inspect -f '{{.State.Health.Status}}' shopsphere-mongo 2>/dev/null)" = "healthy" ]; do sleep 2; done

echo "==> Building (Maven: 5 services + shared lib, Gradle: notification-service)"
./mvnw -q -DskipTests package
(cd notification-service && ./gradlew -q bootJar -x test)

wait_http() { # url name seconds
  local i=0
  until curl -fs -o /dev/null "$1"; do
    sleep 2; i=$((i+2))
    if [ "$i" -ge "$3" ]; then echo "!! $2 did not become healthy, see logs/$2.log"; exit 1; fi
  done
  echo "    $2 is up"
}

start() { # name jar port
  if [ -f ".pids/$1.pid" ] && kill -0 "$(cat .pids/$1.pid)" 2>/dev/null; then echo "    $1 already running"; return; fi
  nohup java -Xmx256m -jar "$2" > "logs/$1.log" 2>&1 &
  echo $! > ".pids/$1.pid"
}

echo "==> Starting services"
start discovery-server discovery-server/target/discovery-server-1.0.0.jar
wait_http http://localhost:8761/actuator/health discovery-server 90
start auth-service auth-service/target/auth-service-1.0.0.jar
start product-service product-service/target/product-service-1.0.0.jar
start order-service order-service/target/order-service-1.0.0.jar
start notification-service notification-service/build/libs/notification-service-1.0.0.jar
wait_http http://localhost:8081/actuator/health auth-service 120
wait_http http://localhost:8082/actuator/health product-service 120
wait_http http://localhost:8083/actuator/health order-service 120
wait_http http://localhost:8084/actuator/health notification-service 120
start api-gateway api-gateway/target/api-gateway-1.0.0.jar
wait_http http://localhost:8080/actuator/health api-gateway 120

echo "==> Starting front end"
if [ ! -f .pids/frontend.pid ] || ! kill -0 "$(cat .pids/frontend.pid)" 2>/dev/null; then
  nohup node frontend/server.js > logs/frontend.log 2>&1 &
  echo $! > .pids/frontend.pid
fi

cat <<MSG

ShopSphere is running
  Web app          http://localhost:3000
  API gateway      http://localhost:8080
  Eureka registry  http://localhost:8761
  Swagger UIs      http://localhost:8082/swagger-ui.html (products)  :8083 (orders)  :8081 (auth)  :8084 (notifications)
  REST Docs        http://localhost:8082/docs/index.html
Demo logins (dev seed only):  admin / Admin#Pass123   alice / Alice#Pass123
Stop everything with scripts/stop-all.sh
MSG
