param(
  [int]$FrontendPort = 5173,
  [string]$HostName = "0.0.0.0"
)

$ErrorActionPreference = "Stop"

$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
$FrontendDir = Join-Path $Root "frontend"

Set-Location $FrontendDir

if (-not (Test-Path (Join-Path $FrontendDir "node_modules"))) {
  npm install
}

$env:VITE_DEV_SERVER_PORT = "$FrontendPort"
npm run dev -- "--host=$HostName" "--port=$FrontendPort"
