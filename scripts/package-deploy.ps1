param(
  [string]$OutputDir = "backup",
  [string]$DbSqlPath = "",
  [switch]$IncludeUploads,
  [switch]$NoClean
)

$ErrorActionPreference = "Stop"

$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$Timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$StageRoot = Join-Path $Root "backup\deploy-stage-$Timestamp"
$Stage = Join-Path $StageRoot "questioncode"
$OutDirAbs = Join-Path $Root $OutputDir
$ZipPath = Join-Path $OutDirAbs "questioncode-deploy-$Timestamp.zip"

function Copy-Path($Source, $Destination) {
  if (Test-Path $Source) {
    Copy-Item -Path $Source -Destination $Destination -Recurse -Force
  }
}

function Copy-DirectoryFiltered($Source, $Destination) {
  if (-not (Test-Path $Source)) {
    return
  }
  New-Item -ItemType Directory -Force -Path $Destination | Out-Null
  $excludeDirs = @("node_modules", "dist", "target", ".m2", ".local-dev", ".git", ".codex", "tmp-spreadsheet-build")
  $excludeFiles = @("*.log", "*.pid")
  & robocopy $Source $Destination /E /XD $excludeDirs /XF $excludeFiles /NFL /NDL /NJH /NJS /NP | Out-Null
  if ($LASTEXITCODE -gt 7) {
    throw "robocopy failed for $Source -> $Destination, exit code $LASTEXITCODE"
  }
}

if (-not $NoClean -and (Test-Path $StageRoot)) {
  Remove-Item -LiteralPath $StageRoot -Recurse -Force
}

New-Item -ItemType Directory -Force -Path $Stage | Out-Null
New-Item -ItemType Directory -Force -Path $OutDirAbs | Out-Null

$dirs = @("backend", "frontend", "docker", "docs", "scripts", "sql")
foreach ($dir in $dirs) {
  Copy-DirectoryFiltered (Join-Path $Root $dir) (Join-Path $Stage $dir)
}

$files = @("docker-compose.yml", "README.md", "LOCAL_DEV.md")
foreach ($file in $files) {
  Copy-Path (Join-Path $Root $file) (Join-Path $Stage $file)
}

if ($IncludeUploads) {
  Copy-DirectoryFiltered (Join-Path $Root "uploads") (Join-Path $Stage "uploads")
} else {
  New-Item -ItemType Directory -Force -Path (Join-Path $Stage "uploads") | Out-Null
}

New-Item -ItemType Directory -Force -Path (Join-Path $Stage "backup") | Out-Null
if ($DbSqlPath) {
  $dbPath = Resolve-Path $DbSqlPath
  Copy-Item -Path $dbPath -Destination (Join-Path $Stage "backup\problem_db_deploy.sql") -Force
} elseif (Test-Path (Join-Path $Root "backup\problem_db_deploy.sql")) {
  Copy-Item -Path (Join-Path $Root "backup\problem_db_deploy.sql") -Destination (Join-Path $Stage "backup\problem_db_deploy.sql") -Force
} elseif (Test-Path (Join-Path $Root "backup\problem_db_deploy_20260610_173403.sql")) {
  Copy-Item -Path (Join-Path $Root "backup\problem_db_deploy_20260610_173403.sql") -Destination (Join-Path $Stage "backup\problem_db_deploy.sql") -Force
}

if (Test-Path $ZipPath) {
  Remove-Item -LiteralPath $ZipPath -Force
}

Compress-Archive -Path $Stage -DestinationPath $ZipPath -Force

Write-Host "Deployment package created:"
Write-Host $ZipPath
Write-Host ""
Write-Host "Next upload example:"
Write-Host "scp `"$ZipPath`" lontri@192.168.0.90:/home/lontri/"
