#!/usr/bin/env bash
# Checks that this machine can run ShopSphere. Prints what is missing and how to get it. Changes nothing.
cd "$(dirname "$0")/.."
FAIL=0; WARN=0
ok()   { printf '  \033[32mOK\033[0m    %s\n' "$1"; }
bad()  { printf '  \033[31mMISSING\033[0m %s\n         -> %s\n' "$1" "$2"; FAIL=1; }
warn() { printf '  \033[33mWARN\033[0m  %s\n         -> %s\n' "$1" "$2"; WARN=1; }

echo "Required tools"
# Java 21+
if command -v java >/dev/null 2>&1; then
  V=$(java -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+)[."].*/\1/')
  [ "${V:-0}" -ge 21 ] 2>/dev/null && ok "Java $V" || bad "Java $V found, need 21 or newer" "install JDK 21 (https://adoptium.net) and make sure JAVA_HOME / PATH point to it"
else bad "Java not found" "install JDK 21 (https://adoptium.net)"; fi
# Node 20+
if command -v node >/dev/null 2>&1; then
  N=$(node -v | sed -E 's/v([0-9]+).*/\1/')
  [ "$N" -ge 20 ] && ok "Node $(node -v)" || bad "Node $(node -v) found, need 20 or newer" "install Node.js 20 LTS (https://nodejs.org)"
else bad "Node.js not found" "install Node.js 20 LTS (https://nodejs.org)"; fi
command -v npm >/dev/null 2>&1 && ok "npm $(npm -v)" || bad "npm not found" "it ships with Node.js"
# Docker
if command -v docker >/dev/null 2>&1; then
  if docker info >/dev/null 2>&1; then ok "Docker running ($(docker --version | cut -d, -f1))"
  else bad "Docker installed but not running" "start Docker Desktop and wait until it says 'running'"; fi
  docker compose version >/dev/null 2>&1 && ok "Docker Compose v2" || bad "'docker compose' not available" "update Docker Desktop (Compose v2 is included)"
else bad "Docker not found" "install Docker Desktop (https://www.docker.com/products/docker-desktop)"; fi
command -v curl >/dev/null 2>&1 && ok "curl" || bad "curl not found" "install curl"
command -v git  >/dev/null 2>&1 && ok "git"  || warn "git not found" "only needed to download the project (or download the ZIP from GitHub)"

echo "Optional tools (only for the test scripts)"
command -v jq >/dev/null 2>&1 && ok "jq" || warn "jq not found" "needed by scripts/smoke-test.sh: brew install jq  |  apt install jq"
command -v python3 >/dev/null 2>&1 && ok "python3" || warn "python3 not found" "only needed to regenerate the Postman collection"

echo "Free network ports"
for p in 3000 8080 8081 8082 8083 8084 8761 27017 9092 6379; do
  if (command -v lsof >/dev/null 2>&1 && lsof -iTCP:$p -sTCP:LISTEN >/dev/null 2>&1) || (command -v ss >/dev/null 2>&1 && ss -ltn 2>/dev/null | grep -q ":$p "); then
    warn "port $p is already in use" "stop whatever uses it (e.g. a local MongoDB/Redis/Kafka), or the platform will not start"
  fi
done
[ $WARN = 0 ] && ok "all required ports look free (3000, 8080-8084, 8761, 27017, 9092, 6379)"

echo "Resources"
MEM=""
if [ "$(uname)" = "Darwin" ]; then MEM=$(( $(sysctl -n hw.memsize) / 1073741824 )); elif [ -r /proc/meminfo ]; then MEM=$(( $(awk '/MemTotal/{print $2}' /proc/meminfo) / 1048576 )); fi
if [ -n "$MEM" ]; then [ "$MEM" -ge 8 ] && ok "${MEM} GB RAM (8 GB minimum, 16 GB comfortable)" || warn "${MEM} GB RAM" "8 GB or more is recommended: 6 JVMs + MongoDB + Kafka + Redis"; fi
FREE=$(df -Pk . | awk 'NR==2{print int($4/1048576)}'); [ "${FREE:-0}" -ge 6 ] && ok "${FREE} GB free disk (about 5 GB needed for images and dependencies)" || warn "${FREE} GB free disk" "about 5 GB are needed for Docker images and Maven/Gradle downloads"
curl -fsS -m 8 -o /dev/null https://repo.maven.apache.org/maven2/ && ok "internet access to Maven Central (first build downloads dependencies)" || warn "cannot reach repo.maven.apache.org" "the first build needs internet or a corporate proxy/mirror (see docs/SETUP.md)"

echo
if [ $FAIL = 0 ]; then echo "Ready. Next: scripts/start-all.sh"; else echo "Fix the MISSING items above, then run this script again."; fi
exit $FAIL
