# Configure l'envoi d'emails de Keycloak (liens d'invitation, mot de passe oublié) vers Mailpit.
# En développement, les emails ne partent pas vers de vraies boîtes : ils sont visibles sur http://localhost:8025.
# Idempotent. Pour un vrai SMTP, passer -SmtpHost/-SmtpPort (et ne jamais committer d'identifiants).
param(
    [string]$SmtpHost = 'mailpit',
    [string]$SmtpPort = '1025',
    [string]$From = 'no-reply@legalflow.local'
)
$ErrorActionPreference = 'Stop'

$keycloakUrl = 'http://localhost:8080'
$realm = 'legalflow'

$tokenResponse = Invoke-RestMethod -Method Post `
    -Uri "$keycloakUrl/realms/master/protocol/openid-connect/token" `
    -ContentType 'application/x-www-form-urlencoded' `
    -Body @{ client_id = 'admin-cli'; grant_type = 'password'; username = 'admin'; password = 'admin' }
$headers = @{ Authorization = "Bearer $($tokenResponse.access_token)" }

# Corps encodé en UTF-8. La virgule empêche PowerShell de dérouler le tableau d'octets renvoyé par la fonction.
function ConvertTo-JsonBytes($Object) {
    return ,[Text.Encoding]::UTF8.GetBytes((ConvertTo-Json -InputObject $Object -Depth 6))
}

$update = @{
    smtpServer = @{
        host            = $SmtpHost
        port            = $SmtpPort
        from            = $From
        fromDisplayName = 'LegalFlow'
        auth            = 'false'
        ssl             = 'false'
        starttls        = 'false'
    }
}
Invoke-RestMethod -Method Put -Uri "$keycloakUrl/admin/realms/$realm" -Headers $headers `
    -ContentType 'application/json; charset=utf-8' -Body (ConvertTo-JsonBytes $update) | Out-Null

$configured = (Invoke-RestMethod -Method Get -Uri "$keycloakUrl/admin/realms/$realm" -Headers $headers).smtpServer
if ($configured.host -eq $SmtpHost -and $configured.port -eq $SmtpPort) {
    Write-Output "PASS: Keycloak enverra ses emails via ${SmtpHost}:${SmtpPort} (expéditeur $($configured.from))."
} else {
    Write-Output "FAIL: la configuration SMTP du realm n'a pas été enregistrée."
    exit 1
}
