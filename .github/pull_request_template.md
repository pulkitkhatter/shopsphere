## What and why
<!-- One or two sentences. Link the issue. -->

## How it was built
- [ ] Written test-first (red -> green -> refactor); the failing test is in the first commit of the branch
- [ ] Pair / mob session? Add `Co-authored-by:` trailers for everyone who drove or navigated

## Checklist
- [ ] `./mvnw verify`, `./gradlew test` (notification-service) and `npm test` (frontend) pass locally
- [ ] New endpoints are documented (OpenAPI annotations + a REST Docs test if public) and covered by `api-tests/`
- [ ] Authorization is enforced server-side (role + ownership) and covered by a test that tries the forbidden case
- [ ] Input is validated and bounded (sizes, page limits, whitelisted sort fields)
- [ ] Breaking API change? Then it is a new version (`/api/v3/...`), the old one gets Deprecation/Sunset headers
- [ ] Events: contract changes are backwards compatible (add fields, never rename/remove) and consumers stay idempotent
