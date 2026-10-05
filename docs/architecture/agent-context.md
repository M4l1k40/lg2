# Agent Context: Tenant Isolation

## Required tenant convention

- Keycloak access tokens contain the `lawFirmId` claim. The claim is administrator-managed and must not be user-editable.
- With Keycloak 26.0.7, import roles, clients, default scopes, and users from `infrastructure/keycloak/legalflow-realm.json`. Leave `clientScopes` unset there so Keycloak creates built-in scopes before processing client default-scope assignments. Then run `infrastructure/scripts/configure-keycloak-user-profile.ps1` to create/map the custom `lawFirmId` scope and apply the admin-only declarative profile. `RealmRepresentation` supports neither this `userProfile` property nor a custom-scope ordering that precedes built-in creation; keep both configurations out of the realm JSON.
- Every resource-server service duplicates its own small `TenantContext.requireLawFirmId()` implementation. Do not create a shared Maven module; service Docker build contexts are isolated.
- Derive tenant ownership exclusively from the authenticated JWT. A matching `lawFirmId` query parameter is tolerated but ignored; a different query/body value returns `403 Forbidden`.
- On create, accept an absent body `lawFirmId`, reject a mismatching supplied value with `403`, and set the persisted tenant from the token.
- For by-ID operations and child resources, check ownership in the service layer. Return `404 Not Found` for resources owned by another tenant. Resolve child ownership through its parent entity.
- Keep `Court` and `Judge` global reference data without a `lawFirmId` field.
- Keep controller paths free of `/api`; configure public `/api/*` routes in the gateway with `StripPrefix(1)`. Forward the `Authorization` header unchanged.
- Preserve internal JWK fetching and pinned issuer validation in every resource server.
- Before creating a resource that references another service's entity (for example, a case's `clientId` or a judicial case's `caseId`), synchronously GET that entity from its owner service and forward the current caller's bearer token unchanged. Do not mint a service token or allow the write when verification fails.
- Use `RestClient`/`WebClient` with a short explicit timeout (3 seconds). Map downstream `404` to a domain `422` with a clear ownership message; map downstream `5xx`, timeouts, and connection failures to `503`. Add tests for the `404` and timeout mappings plus live cross-tenant HTTP assertions.
- Add JWT-based MockMvc tests for own-tenant access, cross-tenant `404`, mismatched query/body `403`, missing-claim `403`, and tenant-filtered lists for every implemented tenant-owned service.
- There is no `deadline-service` implementation yet; apply this convention when that service is created.
