param(
  [int]$MysqlPort = 3307,
  [int]$BackendPort = 8080,
  [int]$FrontendPort = 5173,
  [switch]$NoDocker,
  [switch]$ImportBootstrap
)

$ErrorActionPreference = "Stop"

$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
$LogDir = Join-Path $Root ".local-dev"
New-Item -ItemType Directory -Force -Path $LogDir | Out-Null

function Stop-FromPidFile {
  param([string]$Name)

  $PidFile = Join-Path $LogDir "$Name.pid"
  if (-not (Test-Path $PidFile)) {
    return
  }

  $ProcessId = [int](Get-Content $PidFile)
  $Process = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
  if ($Process) {
    Stop-ProcessTree $ProcessId
  }
  Remove-Item $PidFile -Force -ErrorAction SilentlyContinue
}

function Stop-ProcessTree {
  param([int]$ProcessId)

  $Children = Get-CimInstance Win32_Process -Filter "ParentProcessId=$ProcessId" -ErrorAction SilentlyContinue
  foreach ($Child in $Children) {
    Stop-ProcessTree ([int]$Child.ProcessId)
  }

  $Process = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
  if ($Process) {
    Stop-Process -Id $ProcessId -Force
  }
}

function Assert-PortFree {
  param([int]$Port)

  $Listener = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
  if ($Listener) {
    throw "Port $Port is already in use by process $($Listener.OwningProcess)."
  }
}

function Start-DevProcess {
  param(
    [string]$Name,
    [string]$ScriptPath,
    [string[]]$ScriptArgs
  )

  $OutLog = Join-Path $LogDir "$Name.out.log"
  $ErrLog = Join-Path $LogDir "$Name.err.log"
  $PidFile = Join-Path $LogDir "$Name.pid"

  Remove-Item $OutLog, $ErrLog -Force -ErrorAction SilentlyContinue

  $ArgumentList = @(
    "-NoProfile",
    "-ExecutionPolicy",
    "Bypass",
    "-File",
    $ScriptPath
  ) + $ScriptArgs

  $Process = Start-Process `
    -FilePath "powershell" `
    -ArgumentList $ArgumentList `
    -WindowStyle Hidden `
    -RedirectStandardOutput $OutLog `
    -RedirectStandardError $ErrLog `
    -PassThru

  Set-Content -Path $PidFile -Value $Process.Id
  Write-Host "$Name started. pid=$($Process.Id), stdout=$OutLog, stderr=$ErrLog"
}

function Wait-HttpOk {
  param(
    [string]$Name,
    [string]$Url,
    [int]$TimeoutSeconds = 90
  )

  $Deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  Write-Host "Waiting for ${Name}: $Url"

  while ((Get-Date) -lt $Deadline) {
    try {
      $Response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 3
      if ($Response.StatusCode -eq 200) {
        Write-Host "$Name is ready."
        return
      }
    } catch {
      Start-Sleep -Seconds 2
    }
  }

  throw "$Name did not become ready within $TimeoutSeconds seconds. Check logs in $LogDir."
}

Stop-FromPidFile "backend"
Stop-FromPidFile "frontend"

Assert-PortFree $BackendPort
Assert-PortFree $FrontendPort

if (-not $NoDocker) {
  Push-Location $Root
  docker compose up -d mysql
  docker compose stop backend frontend | Out-Null
  Pop-Location
}

$BackendArgs = @(
  "-MysqlPort", "$MysqlPort",
  "-BackendPort", "$BackendPort"
)
if ($ImportBootstrap) {
  $BackendArgs += "-ImportBootstrap"
}

Start-DevProcess `
  -Name "backend" `
  -ScriptPath (Join-Path $PSScriptRoot "dev-backend.ps1") `
  -ScriptArgs $BackendArgs

Start-DevProcess `
  -Name "frontend" `
  -ScriptPath (Join-Path $PSScriptRoot "dev-frontend.ps1") `
  -ScriptArgs @("-FrontendPort", "$FrontendPort")

Write-Host "Local dev mode:"
Write-Host "  frontend: http://localhost:$FrontendPort"
Write-Host "  backend:  http://localhost:$BackendPort/api/v1"
Write-Host "  health:   http://localhost:$BackendPort/actuator/health"

Wait-HttpOk -Name "backend" -Url "http://localhost:$BackendPort/actuator/health" -TimeoutSeconds 120
Wait-HttpOk -Name "frontend" -Url "http://localhost:$FrontendPort" -TimeoutSeconds 60
