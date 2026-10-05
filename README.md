# LegalFlow

## Local Stack

Start the services from `infrastructure`:

```powershell
docker compose up -d --build
```

The stack includes the consultation microservice at `http://localhost:9000/api/consultations`.

After Keycloak is healthy, apply the admin-only `lawFirmId` user-profile policy. Re-run this after recreating Keycloak, since its development database is container-local:

```powershell
.\scripts\configure-keycloak-user-profile.ps1
```

Run the live tenant-isolation and gateway checks with:

```powershell
.\scripts\test-tenant-isolation.ps1
```