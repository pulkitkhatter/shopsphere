# Setup guide (for a new machine)

Goal: from a clean laptop to the running shop in about 15 minutes (the first start downloads dependencies).

## 1. What to download and install

You install **four things**. Everything else (Maven, Gradle, MongoDB, Kafka, Redis) is downloaded automatically by the project.

| # | Tool | Version | Why it is needed | Download |
|---|---|---|---|---|
| 1 | **JDK (Java)** | **21** or newer | Runs and builds the 6 Spring Boot services | https://adoptium.net (Temurin 21 LTS) |
| 2 | **Node.js** (includes npm) | **20** LTS or newer | Runs the web app and the front-end tests | https://nodejs.org |
| 3 | **Docker Desktop** | current (Compose v2 included) | Runs MongoDB, Kafka and Redis in containers | https://www.docker.com/products/docker-desktop |
| 4 | **Git** | any | Downloads the project (or use "Download ZIP" on GitHub) | https://git-scm.com |

Optional, only for the test scripts: **jq** (`brew install jq` / `sudo apt install jq` / `choco install jq`) and **Python 3**.

**Not needed:** Maven and Gradle (the project ships `./mvnw` and `./gradlew` wrappers that fetch their own copy), a local MongoDB/Kafka/Redis, an IDE.

### Operating system
| OS | Notes |
|---|---|
| **macOS** | Works as is. `brew install openjdk@21 node git jq` plus Docker Desktop is the quickest route. |
| **Linux** | Works as is. Install Docker Engine + the compose plugin, JDK 21, Node 20. |
| **Windows** | The scripts are Bash. Use **WSL2** (recommended): install WSL2 + Ubuntu, enable Docker Desktop's WSL integration, then install JDK 21 / Node 20 *inside* Ubuntu and run everything from the Ubuntu terminal. Git Bash can work but is not tested. |

### Machine requirements
* **RAM:** 8 GB minimum, 16 GB comfortable (6 Java services + MongoDB + Kafka + Redis). In Docker Desktop give containers at least 4 GB.
* **Disk:** about 5 GB free (Docker images about 1 GB, Maven/Gradle dependency downloads, build output).
* **Internet** on the first run: Maven Central, Docker Hub (images `mongo:7`, `apache/kafka:3.8.0`, `redis:7-alpine`) and `services.gradle.org`. Later runs work offline.
* **Free ports:** 3000, 8080-8084, 8761, 27017, 9092, 6379. Stop any local MongoDB / Redis / Kafka that already uses them.

## 2. Get the project
```bash
git clone https://github.com/pulkitkhatter/shopsphere.git
cd shopsphere
```
(No Git? On the GitHub page choose Code → Download ZIP and unzip it.)

## 3. Check the machine
```bash
scripts/check-prereqs.sh
```
It lists anything missing with the exact fix (Java/Node versions, Docker running, free ports, RAM, disk, internet). Run it again until it ends with "Ready".

## 4. Start everything
Make sure Docker Desktop is running, then:
```bash
scripts/start-all.sh
```
What it does: starts MongoDB, Kafka and Redis in Docker, builds all services (first time: a few minutes), starts the six services and the web app, and waits until each reports healthy. It ends with a list of URLs.

## 5. Open it
| What | URL |
|---|---|
| **The shop** | http://localhost:3000 |
| API gateway | http://localhost:8080 |
| Service registry | http://localhost:8761 |
| API explorer (Swagger) | http://localhost:8082/swagger-ui.html |
| Generated API docs | http://localhost:8082/docs/index.html |

Demo accounts are created by the development seed (`admin` = administrator, `alice` = customer). Their passwords are in `auth-service/src/main/resources/application.yml`; you can also register a new customer in the web app.

## 6. Verify it works (optional, about 1 minute)
```bash
scripts/smoke-test.sh          # 61 end-to-end checks; needs jq
```
Other checks: `scripts/resilience-demo.sh` (kills services on purpose and shows recovery), `npx newman run api-tests/shopsphere.postman_collection.json` (API tests), `./mvnw verify`, `(cd notification-service && ./gradlew test)`, `(cd frontend && npm test)` (unit tests; the Maven integration tests need Docker).

## 7. Stop
```bash
scripts/stop-all.sh            # stops the services and web app
scripts/stop-all.sh --infra    # also stops MongoDB, Kafka, Redis
```
Data (products, users, orders) is kept in Docker volumes between runs. To start completely clean: `scripts/stop-all.sh --infra && docker volume rm shopsphere_mongo-data shopsphere_kafka-data`.

## 8. Troubleshooting
| Symptom | Fix |
|---|---|
| `Cannot connect to the Docker daemon` | Start Docker Desktop and wait until it is running. |
| `Port ... is already allocated / in use` | Stop the program using it (`lsof -i :8080`), often a local MongoDB/Redis/Kafka. |
| `release version 21 not supported` / `invalid target release` | Java is older than 21. Check `java -version` and `JAVA_HOME`. |
| The shop is blank or shows "Could not load products" right after start | Services need 20-40 s to register with each other; wait and reload. |
| Login says "Too many requests" (429) | The login rate limit; wait about 10 seconds. |
| `Services are already running` when starting | Run `scripts/stop-all.sh` first (rebuilding under running services breaks them). |
| Build cannot download dependencies | Corporate proxy: add your proxy to `~/.m2/settings.xml` and `~/.gradle/gradle.properties`, or point both to your internal Maven mirror. Docker needs the proxy set in Docker Desktop settings. |
| Services fail with out-of-memory | Raise Docker Desktop memory (4 GB+) and close other heavy apps. |
| Windows: `bad interpreter` / `^M` errors | Files were checked out with CRLF line endings: `git config core.autocrlf false`, re-clone inside WSL2. |
| Logs | `logs/<service>.log` in the project folder. |

## 9. Running it on a server instead of a laptop
The same steps work on a Linux VM (4 vCPU / 16 GB recommended). To reach the app from other machines, expose ports 3000 (web) and 8080 (gateway) only, and set `ALLOWED_ORIGINS=http://<server-address>:3000` and `window.__API_BASE__` / `API_ORIGIN` accordingly. This is a **development setup** (no TLS, demo users, unauthenticated Eureka); see `docs/SECURITY.md` for the production checklist before exposing it publicly.
