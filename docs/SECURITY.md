# Security

## Authentication and authorization

### Tokens (OAuth 2.0 / OpenID Connect style)
* **Token endpoint** `POST /oauth/token` (form encoded): `grant_type=password` and `grant_type=refresh_token`. Errors follow RFC 6749 (`invalid_grant`, `unsupported_grant_type`, ...). Responses carry `Cache-Control: no-store`.
* **Access token**: JWT signed with **RS256**, 15 minutes, claims `iss`, `sub`, `iat`, `exp`, `jti`, `roles`, `email`, `scope`.
* **Public keys**: `GET /.well-known/jwks.json`. Every other component verifies tokens with that public key; **only auth-service holds the private key**, so a compromise of any other service cannot mint tokens. The test suite asserts the JWKS contains no private parameter (`d`).
* **Discovery**: `GET /.well-known/openid-configuration` and `GET /userinfo`.
* **Validation everywhere**: signature, expiry/not-before, **issuer** (`JwtValidators.createDefaultWithIssuer`) at the gateway *and* in each service.
* **Refresh tokens**: 256-bit random, stored only as SHA-256 hash, **single use (rotation)**, 7 days, MongoDB TTL index removes expired ones. Re-using an old refresh token is rejected (smoke test + Newman).
* **Passwords**: BCrypt cost 12; policy 10-72 characters (72 = BCrypt's input limit); login compares against a dummy hash for unknown users so timing and message (`Bad credentials`) do not reveal which usernames exist.
* **Roles**: self-registration can only ever create `USER`; admin accounts are provisioned out of band. Roles are mapped from the `roles` claim to `ROLE_*` authorities (`JwtRoles`).

### Authorization rules (enforced on the server, tested with the forbidden case)
| Rule | Where | Test |
|---|---|---|
| Catalogue reads are public, writes need `ADMIN` | `product-service SecurityConfig` | `ProductApiTest` (401 without token, 403 as customer) |
| An order can be read/cancelled only by its owner or an admin; others get **404** (not 403) so ids cannot be probed | `OrderService.get` | `OrderServiceTest`, smoke test, Newman |
| Order list of everyone is `ADMIN` only | `order-service SecurityConfig` | `OrderApiTest` |
| Notifications: always the caller's own; there is **no** `userId` parameter to tamper with | `NotificationController` | `NotificationApiTest` ("userId=bob" is ignored) |
| Unknown paths are denied (`anyRequest().denyAll()`), the gateway requires auth for anything not whitelisted | all `SecurityConfig`s | `ProductApiTest`, `GatewaySecurityTest` |
| Prices and totals are never taken from the client | `OrderRequest` has no price field | `OrderApiTest`, smoke test, Newman |

## OWASP Top 10 (2021) mapping

| # | Risk | What this project does |
|---|---|---|
| A01 | Broken access control | Deny-by-default; role + ownership checks in the service layer; IDOR-safe 404s; admin-only listings; client-supplied user ids ignored; CORS allow-list (no `*`) |
| A02 | Cryptographic failures | RS256 signatures, BCrypt, hashed refresh tokens, `Cache-Control: no-store` on tokens, HSTS header on auth-service. **Gap:** no TLS in the dev setup (terminate TLS at the gateway/ingress in production) |
| A03 | Injection | Spring Data parameter binding (no string-built queries); sort fields whitelisted; product id path-pattern validated (`[A-Za-z0-9]{1,64}`); `RestClient` URI templates encode path values; front end builds DOM through `textContent` only (no `innerHTML`), CSP forbids inline script |
| A04 | Insecure design | Idempotency keys, atomic stock decrement, optimistic locking, bounded page sizes/lists/quantities, rate limits, circuit breakers, outbox |
| A05 | Security misconfiguration | Only whitelisted gateway routes (`discovery.locator.enabled=false`); actuator exposes only health/info/metrics (and only health/info/prometheus without auth); stack traces and exception text never leave the service (`ApiExceptionHandler`); security headers (CSP, X-Frame-Options, nosniff, Referrer-Policy) on the web app; demo seed is a flag. **Gap:** Eureka and actuator metrics are unauthenticated in dev |
| A06 | Vulnerable and outdated components | Spring Boot BOM manages versions; Testcontainers pinned newer than the BOM for Docker 29 compatibility; recommended: Dependabot + `mvn org.owasp:dependency-check-maven` in CI (not wired up) |
| A07 | Identification and authentication failures | Rotating refresh tokens, short access tokens, uniform error for bad user/password, per-IP login rate limit (burst 10, 2/s; verified: parallel attempts get 429), password length policy. **Gaps:** no MFA, no account lockout, no breached-password check |
| A08 | Software and data integrity failures | Signed tokens verified with issuer check; Kafka payloads deserialised only into a fixed type with `trusted.packages` restricted and **no type headers**, so a message cannot name an arbitrary class; `ErrorHandlingDeserializer` + DLT |
| A09 | Logging and monitoring failures | Correlation id on every request across gateway and services (header validated against `[A-Za-z0-9-]{8,64}` to prevent log forging), failed logins logged with sanitised username, Prometheus metrics, circuit breaker and cache metrics |
| A10 | SSRF | The only server-side outbound call (`order-service -> product-service`) targets a fixed service name; the path variable is validated and URL-encoded, so user input cannot change host or path |

## Browser-side (front end)
* Access token **in memory only**; refresh token in `sessionStorage` (cleared when the tab closes). Trade-off: an XSS bug could still use the session, which is why the first line of defence is a strict CSP + no `innerHTML`. `HttpOnly` cookies would be an alternative that needs CSRF protection.
* Concurrent 401s share **one** refresh call, because refresh tokens are single-use (otherwise parallel refreshes would log the user out) - covered by `api.test.js`.
* CSP: `default-src 'self'; script-src 'self'; connect-src 'self' <gateway>; frame-ancestors 'none'; base-uri 'none'` set by `frontend/server.js` and tested; static server blocks `../` traversal (tested).

## SAML - what is and is not here
**Not implemented.** SAML 2.0 is a browser-redirect SSO protocol between an organisation's identity provider (Okta, ADFS, Azure AD, ...) and a *web application*; a first-party REST API protected by bearer tokens does not need it. Two ways to add it when an enterprise customer asks:
1. **Federate at the IdP** (recommended): put Keycloak/Cognito/Auth0 in front, configure the customer's SAML IdP as an identity provider there, and keep this platform consuming OIDC/JWT tokens exactly as now - no service changes except pointing `shopsphere.security.jwk-set-uri` / `issuer` at the IdP.
2. **In-app**: add `spring-security-saml2-service-provider` to a login-facing service, configure a `RelyingPartyRegistration` (IdP metadata URL, signing certificates, ACS URL) and `http.saml2Login()`, then exchange the resulting `Saml2Authentication` for the platform's own JWT in auth-service (map SAML attributes to `roles`).

## Production checklist (deliberately not done in this dev setup)
TLS everywhere (gateway termination + mTLS or a service mesh internally) · secrets from a vault, not `application.yml` · persistent, rotated signing keys (KMS/HSM, several `kid`s) · disable the demo seed · authenticate Eureka and restrict actuator to an internal network · enable MongoDB/Redis/Kafka authentication and network policies · run dependency and container scanning in CI · add MFA / lockout / breached-password checks · trust `X-Forwarded-For` only from the known load balancer · set `eureka.server.enable-self-preservation=true`.
