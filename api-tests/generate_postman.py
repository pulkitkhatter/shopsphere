#!/usr/bin/env python3
"""Generates shopsphere.postman_collection.json (Postman v2.1). Run: python3 api-tests/generate_postman.py"""
import json, pathlib

def req(name, method, path, tests, body=None, auth=None, headers=None, form=None, pre=None):
    h = [{"key": k, "value": v} for k, v in (headers or {}).items()]
    r = {"method": method, "header": h, "url": {"raw": "{{baseUrl}}" + path, "host": ["{{baseUrl}}"], "path": [p for p in path.split("?")[0].split("/") if p],
         "query": [{"key": q.split("=")[0], "value": q.split("=", 1)[1]} for q in path.split("?")[1].split("&")] if "?" in path else []}}
    if auth: r["auth"] = {"type": "bearer", "bearer": [{"key": "token", "value": "{{" + auth + "}}", "type": "string"}]}
    if body is not None:
        h.append({"key": "Content-Type", "value": "application/json"})
        r["body"] = {"mode": "raw", "raw": json.dumps(body, indent=2)}
    if form:
        r["body"] = {"mode": "urlencoded", "urlencoded": [{"key": k, "value": v} for k, v in form.items()]}
    events = [{"listen": "test", "script": {"type": "text/javascript", "exec": tests}}]
    if pre: events.append({"listen": "prerequest", "script": {"type": "text/javascript", "exec": pre}})
    return {"name": name, "request": r, "event": events}

def t(*lines): return list(lines)
status = lambda code: f"pm.test('status is {code}', () => pm.response.to.have.status({code}));"
rate_pause = ["setTimeout(() => {}, 700);  // login endpoints are rate limited per IP (burst 10, 2/s)"]

auth = {"name": "1. Auth", "item": [
    req("Register new customer", "POST", "/auth/register", t(status(201),
        "pm.test('only USER role', () => pm.expect(pm.response.json().roles).to.eql(['USER']));",
        "pm.test('no password hash leaked', () => pm.expect(pm.response.text()).to.not.include('assword'));"),
        body={"username": "{{newUser}}", "email": "{{newUser}}@example.com", "password": "a-long-enough-pass"},
        pre=["pm.collectionVariables.set('newUser', 'pm' + Date.now().toString().slice(-8));"]),
    req("Register with weak password -> 400", "POST", "/auth/register", t(status(400),
        "pm.test('field errors listed', () => pm.expect(pm.response.json().errors).to.have.property('password'));"),
        body={"username": "weakpw", "email": "weak@example.com", "password": "short"}),
    req("Login as admin", "POST", "/oauth/token", t(status(200),
        "const b = pm.response.json(); pm.collectionVariables.set('adminToken', b.access_token);",
        "pm.test('bearer token + refresh token', () => { pm.expect(b.token_type).to.eql('Bearer'); pm.expect(b.refresh_token).to.be.a('string'); });",
        "pm.test('response is not cacheable', () => pm.expect(pm.response.headers.get('Cache-Control')).to.include('no-store'));",
        "const claims = JSON.parse(atob(b.access_token.split('.')[1])); pm.test('JWT carries roles', () => pm.expect(claims.roles).to.include('ADMIN'));"),
        form={"grant_type": "password", "username": "admin", "password": "{{adminPassword}}"}, pre=rate_pause),
    req("Login as customer (alice)", "POST", "/oauth/token", t(status(200),
        "const b = pm.response.json(); pm.collectionVariables.set('aliceToken', b.access_token); pm.collectionVariables.set('aliceRefresh', b.refresh_token);"),
        form={"grant_type": "password", "username": "alice", "password": "{{alicePassword}}"}, pre=rate_pause),
    req("Login with wrong password -> 400 invalid_grant", "POST", "/oauth/token", t(status(400),
        "pm.test('RFC 6749 error', () => pm.expect(pm.response.json().error).to.eql('invalid_grant'));"),
        form={"grant_type": "password", "username": "alice", "password": "nope"}, pre=rate_pause),
    req("Refresh token (rotates)", "POST", "/oauth/token", t(status(200),
        "const b = pm.response.json(); pm.test('new refresh token differs', () => pm.expect(b.refresh_token).to.not.eql(pm.collectionVariables.get('aliceRefresh')));",
        "pm.collectionVariables.set('aliceToken', b.access_token); pm.collectionVariables.set('usedRefresh', pm.collectionVariables.get('aliceRefresh'));"),
        form={"grant_type": "refresh_token", "refresh_token": "{{aliceRefresh}}"}, pre=rate_pause),
    req("Reuse the old refresh token -> 400", "POST", "/oauth/token", t(status(400)),
        form={"grant_type": "refresh_token", "refresh_token": "{{usedRefresh}}"}, pre=rate_pause),
    req("JWKS (public keys)", "GET", "/.well-known/jwks.json", t(status(200),
        "pm.test('RS256 key and no private part', () => { const k = pm.response.json().keys[0]; pm.expect(k.alg).to.eql('RS256'); pm.expect(k).to.not.have.property('d'); });")),
    req("UserInfo", "GET", "/userinfo", t(status(200), "pm.test('subject is alice', () => pm.expect(pm.response.json().sub).to.eql('alice'));"), auth="aliceToken"),
    req("UserInfo without token -> 401", "GET", "/userinfo", t(status(401))),
]}

products = {"name": "2. Products", "item": [
    req("List v2 (public, paginated)", "GET", "/api/v2/products?size=3&sort=price,asc", t(status(200),
        "const b = pm.response.json(); pm.test('page envelope', () => { pm.expect(b.content).to.have.length.within(1, 3); pm.expect(b.totalElements).to.be.above(0); });",
        "pm.test('price is an object (v2 contract)', () => pm.expect(b.content[0].price).to.have.property('amount'));",
        "pm.test('sorted by price ascending', () => pm.expect(b.content[0].price.amount).to.be.at.most(b.content[1].price.amount));")),
    req("Search: q + category + in stock", "GET", "/api/v2/products?q=book&category=books&inStock=true", t(status(200),
        "pm.test('all results in stock', () => pm.response.json().content.forEach(p => pm.expect(p.inStock).to.be.true));")),
    req("Oversized page -> 400", "GET", "/api/v2/products?size=1000", t(status(400))),
    req("Sort on a non-whitelisted field -> 400", "GET", "/api/v2/products?sort=passwordHash,asc", t(status(400))),
    req("v1 list is deprecated", "GET", "/api/v1/products", t(status(200),
        "pm.test('Deprecation + Sunset + Link headers', () => { pm.expect(pm.response.headers.get('Deprecation')).to.eql('true'); pm.expect(pm.response.headers.get('Sunset')).to.be.ok; pm.expect(pm.response.headers.get('Link')).to.include('successor-version'); });",
        "pm.test('v1 keeps the flat price', () => pm.expect(pm.response.json()[0].price).to.be.a('number'));")),
    req("Create product without token -> 401", "POST", "/api/v2/products", t(status(401)), body={"sku": "X"}),
    req("Create product as customer -> 403", "POST", "/api/v2/products", t(status(403)), auth="aliceToken",
        body={"sku": "PM-1", "name": "n", "category": "c", "price": 1, "stock": 1}),
    req("Create product as admin -> 201", "POST", "/api/v2/products", t(status(201),
        "const p = pm.response.json(); pm.collectionVariables.set('productId', p.id);",
        "pm.test('Location header points at the new product', () => pm.expect(pm.response.headers.get('Location')).to.include(p.id));",
        "pm.test('price kept as exact decimal', () => pm.expect(p.price.amount).to.eql(12.34));"),
        auth="adminToken", body={"sku": "{{sku}}", "name": "Postman Gadget", "description": "created by the API test run", "category": "postman", "price": 12.34, "stock": 7, "tags": ["api-test"]},
        pre=["pm.collectionVariables.set('sku', 'PM-' + Date.now());"]),
    req("Create duplicate SKU -> 409", "POST", "/api/v2/products", t(status(409)), auth="adminToken",
        body={"sku": "{{sku}}", "name": "Dup", "category": "postman", "price": 1, "stock": 1}),
    req("Validation errors -> 400 problem+json", "POST", "/api/v2/products", t(status(400),
        "pm.test('RFC 7807 content type', () => pm.expect(pm.response.headers.get('Content-Type')).to.include('application/problem+json'));",
        "pm.test('errors per field', () => pm.expect(pm.response.json().errors).to.include.keys('sku', 'price'));"),
        auth="adminToken", body={"sku": "bad sku!", "name": "x", "category": "c", "price": -5, "stock": 1}),
    req("Get product (cached)", "GET", "/api/v2/products/{{productId}}", t(status(200), "pm.test('stock 7', () => pm.expect(pm.response.json().stock).to.eql(7));")),
    req("Update product (evicts cache)", "PUT", "/api/v2/products/{{productId}}", t(status(200), "pm.test('version incremented (optimistic locking)', () => pm.expect(pm.response.json().version).to.be.above(0));"),
        auth="adminToken", body={"sku": "{{sku}}", "name": "Postman Gadget Pro", "description": "updated", "category": "postman", "price": 15.00, "stock": 7, "tags": ["api-test"]}),
    req("Get product shows fresh data", "GET", "/api/v2/products/{{productId}}", t(status(200), "pm.test('new price visible', () => pm.expect(pm.response.json().price.amount).to.eql(15));")),
]}

orders = {"name": "3. Orders", "item": [
    req("Place order (Idempotency-Key)", "POST", "/api/v1/orders", t(status(201),
        "const o = pm.response.json(); pm.collectionVariables.set('orderId', o.id);",
        "pm.test('total computed by the server from catalogue prices', () => pm.expect(o.total).to.eql(30));",
        "pm.test('Location header', () => pm.expect(pm.response.headers.get('Location')).to.include(o.id));"),
        auth="aliceToken", headers={"Idempotency-Key": "{{idemKey}}"},
        body={"items": [{"productId": "{{productId}}", "quantity": 2}]},
        pre=["pm.collectionVariables.set('idemKey', 'pm-' + Date.now() + '-key');"]),
    req("Replay same Idempotency-Key -> 200, same order", "POST", "/api/v1/orders", t(status(200),
        "pm.test('same order id, nothing duplicated', () => pm.expect(pm.response.json().id).to.eql(pm.collectionVariables.get('orderId')));"),
        auth="aliceToken", headers={"Idempotency-Key": "{{idemKey}}"}, body={"items": [{"productId": "{{productId}}", "quantity": 2}]}),
    req("Client-supplied price is ignored", "POST", "/api/v1/orders", t(status(201),
        "pm.test('priced from catalogue (15.00), not 0.01', () => pm.expect(pm.response.json().total).to.eql(15));",
        "pm.collectionVariables.set('order2', pm.response.json().id);"),
        auth="aliceToken", body={"items": [{"productId": "{{productId}}", "quantity": 1, "price": 0.01}], "total": 0.01}),
    req("Not enough stock -> 422", "POST", "/api/v1/orders", t(status(422)), auth="aliceToken", body={"items": [{"productId": "{{productId}}", "quantity": 99}]}),
    req("Unknown product -> 422", "POST", "/api/v1/orders", t(status(422)), auth="aliceToken", body={"items": [{"productId": "doesnotexist1", "quantity": 1}]}),
    req("Empty order -> 400", "POST", "/api/v1/orders", t(status(400)), auth="aliceToken", body={"items": []}),
    req("Orders need authentication -> 401", "GET", "/api/v1/orders", t(status(401))),
    req("My orders", "GET", "/api/v1/orders", t(status(200), "pm.test('contains the order', () => pm.expect(pm.response.json().content.map(o => o.id)).to.include(pm.collectionVariables.get('orderId')));"), auth="aliceToken"),
    req("Another user cannot read it -> 404", "GET", "/api/v1/orders/{{orderId}}", t(status(404)), auth="adminTokenAsOther"),
    req("Admin can read any order", "GET", "/api/v1/orders/{{orderId}}", t(status(200)), auth="adminToken"),
    req("All orders is admin only -> 403", "GET", "/api/v1/orders/all", t(status(403)), auth="aliceToken"),
    req("Cancel order", "POST", "/api/v1/orders/{{orderId}}/cancel", t(status(200), "pm.test('CANCELLED', () => pm.expect(pm.response.json().status).to.eql('CANCELLED'));"), auth="aliceToken"),
    req("Cancel again -> 409", "POST", "/api/v1/orders/{{orderId}}/cancel", t(status(409)), auth="aliceToken"),
    req("Notifications (async via Kafka)", "GET", "/api/v1/notifications", t(status(200),
        "pm.test('order events produced notifications', () => pm.expect(pm.response.json().totalElements).to.be.above(0));"), auth="aliceToken"),
    req("Cleanup: delete product", "DELETE", "/api/v2/products/{{productId}}", t(status(204)), auth="adminToken"),
]}
# "adminTokenAsOther": a second customer is needed for the 404 check; reuse the freshly registered user's token
auth["item"].insert(3, req("Login as the new customer", "POST", "/oauth/token", t(status(200), "pm.collectionVariables.set('adminTokenAsOther', pm.response.json().access_token);"),
    form={"grant_type": "password", "username": "{{newUser}}", "password": "a-long-enough-pass"}, pre=rate_pause))

collection = {
    "info": {"name": "ShopSphere API", "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
             "description": "End-to-end API tests through the gateway. Run with Newman: npx newman run api-tests/shopsphere.postman_collection.json"},
    "variable": [{"key": "baseUrl", "value": "http://localhost:8080"}, {"key": "adminPassword", "value": "Admin#Pass123"}, {"key": "alicePassword", "value": "Alice#Pass123"}],
    "item": [auth, products, orders],
}
out = pathlib.Path(__file__).with_name("shopsphere.postman_collection.json")
out.write_text(json.dumps(collection, indent=2))
print("wrote", out, sum(len(f["item"]) for f in collection["item"]), "requests")
