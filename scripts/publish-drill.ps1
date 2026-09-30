# 发布演练：dry 提示 + 调用 Admin 热更与审计（需服务已启动且配置 Admin 鉴权）
# 用法: .\scripts\publish-drill.ps1 -AdminBase http://127.0.0.1:8985 -ApiKey $env:ADMIN_API_KEY -AdminUserId 1

param(
    [string]$AdminBase = "http://127.0.0.1:8985",
    [string]$ApiKey = $env:ADMIN_API_KEY,
    [long]$AdminUserId = 1
)

$ErrorActionPreference = "Stop"
if ([string]::IsNullOrWhiteSpace($ApiKey)) {
    Write-Error "ADMIN_API_KEY / -ApiKey is required"
}

$headers = @{
    "X-Admin-User-Id" = "$AdminUserId"
    "X-Admin-Api-Key" = $ApiKey
}

Write-Host "==> POST $AdminBase/admin/ops/reload"
$reload = Invoke-RestMethod -Method Post -Uri "$AdminBase/admin/ops/reload" -Headers $headers
$reload | ConvertTo-Json -Depth 6

Write-Host "==> GET $AdminBase/admin/ops/publish-history"
$history = Invoke-RestMethod -Method Get -Uri "$AdminBase/admin/ops/publish-history?limit=5" -Headers $headers
$history | ConvertTo-Json -Depth 6

if (-not $reload.ok) {
    exit 1
}
Write-Host "Publish drill OK"
