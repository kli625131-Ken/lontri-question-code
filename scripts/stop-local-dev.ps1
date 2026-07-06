$ErrorActionPreference = "Stop"

$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
$LogDir = Join-Path $Root ".local-dev"

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

foreach ($Name in @("backend", "frontend")) {
  $PidFile = Join-Path $LogDir "$Name.pid"
  if (-not (Test-Path $PidFile)) {
    Write-Host "$Name is not running from local dev mode."
    continue
  }

  $ProcessId = [int](Get-Content $PidFile)
  $Process = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
  if ($Process) {
    Stop-ProcessTree $ProcessId
    Write-Host "$Name stopped. pid=$ProcessId"
  } else {
    Write-Host "$Name process was not found. pid=$ProcessId"
  }

  Remove-Item $PidFile -Force -ErrorAction SilentlyContinue
}
