param(
  [string]$ContainerName = "lontri-mysql",
  [string]$Database = "problem_db",
  [string]$DockerRootPassword = $env:MYSQL_ROOT_PASSWORD,
  [string]$LocalHostName = "127.0.0.1",
  [int]$LocalPort = 3306,
  [string]$LocalRootUser = "root",
  [string]$LocalRootPassword = $env:LOCAL_MYSQL_ROOT_PASSWORD,
  [string]$AppUser = "lontri",
  [string]$AppPassword = $env:MYSQL_PASSWORD,
  [string]$BackupDir = "backup",
  [string]$DumpFile = "",
  [switch]$SkipExport,
  [switch]$ExportOnly,
  [switch]$Force
)

$ErrorActionPreference = "Stop"

$Root = Resolve-Path (Join-Path $PSScriptRoot "..")

function Assert-CommandExists {
  param([string]$CommandName)

  if (-not (Get-Command $CommandName -ErrorAction SilentlyContinue)) {
    throw "Command '$CommandName' was not found in PATH."
  }
}

function Assert-SafeIdentifier {
  param(
    [string]$Name,
    [string]$Value
  )

  if ($Value -notmatch '^[A-Za-z0-9_]+$') {
    throw "$Name only supports letters, numbers, and underscore. Current value: $Value"
  }
}

function Escape-SqlLiteral {
  param([string]$Value)
  return ($Value -replace "'", "''")
}

function Escape-ShSingleQuoted {
  param([string]$Value)
  return ($Value -replace "'", "'\''")
}

function Invoke-Native {
  param(
    [string]$FilePath,
    [string[]]$Arguments,
    [string]$FailureMessage
  )

  & $FilePath @Arguments
  if ($LASTEXITCODE -ne 0) {
    throw $FailureMessage
  }
}

function Wait-DockerMysql {
  param(
    [string]$Name,
    [string]$RootPassword,
    [int]$TimeoutSeconds = 90
  )

  $Deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  $EscapedPassword = Escape-ShSingleQuoted $RootPassword
  $PingCommand = "mysqladmin ping -uroot -p'$EscapedPassword' --silent"

  Write-Host "Waiting for Docker mysql container '$Name'..."
  while ((Get-Date) -lt $Deadline) {
    & docker exec $Name sh -c $PingCommand | Out-Null
    if ($LASTEXITCODE -eq 0) {
      Write-Host "Docker mysql is ready."
      return
    }
    Start-Sleep -Seconds 2
  }

  throw "Docker mysql did not become ready within $TimeoutSeconds seconds."
}

Assert-SafeIdentifier -Name "Database" -Value $Database
Assert-SafeIdentifier -Name "AppUser" -Value $AppUser
Assert-CommandExists -CommandName "mysql"

if (-not $SkipExport) {
  Assert-CommandExists -CommandName "docker"
}

if ($DumpFile) {
  $DumpPath = $DumpFile
  if (-not [System.IO.Path]::IsPathRooted($DumpPath)) {
    $DumpPath = Join-Path $Root $DumpPath
  }
} else {
  $Timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
  $BackupPath = $BackupDir
  if (-not [System.IO.Path]::IsPathRooted($BackupPath)) {
    $BackupPath = Join-Path $Root $BackupPath
  }
  $DumpPath = Join-Path $BackupPath "${Database}_${Timestamp}.sql"
}

$DumpParent = Split-Path -Parent $DumpPath
New-Item -ItemType Directory -Force -Path $DumpParent | Out-Null

if ($SkipExport) {
  if (-not (Test-Path $DumpPath)) {
    throw "Dump file does not exist: $DumpPath"
  }
  Write-Host "Skip export. Using dump file: $DumpPath"
} else {
  Push-Location $Root
  try {
    Write-Host "Starting Docker mysql service..."
    Invoke-Native -FilePath "docker" -Arguments @("compose", "up", "-d", "mysql") -FailureMessage "Failed to start Docker mysql service."

    Write-Host "Stopping Docker backend/frontend services..."
    & docker compose stop backend frontend | Out-Null

    Wait-DockerMysql -Name $ContainerName -RootPassword $DockerRootPassword

    $RemoteDumpName = "${Database}_local_migration.sql"
    $RemoteDumpPath = "/tmp/$RemoteDumpName"
    $EscapedDockerRootPassword = Escape-ShSingleQuoted $DockerRootPassword
    $DumpCommand = "mysqldump -uroot -p'$EscapedDockerRootPassword' --single-transaction --routines --triggers --events --default-character-set=utf8mb4 $Database > $RemoteDumpPath"

    Write-Host "Exporting Docker database '$Database' from container '$ContainerName'..."
    Invoke-Native -FilePath "docker" -Arguments @("exec", $ContainerName, "sh", "-c", $DumpCommand) -FailureMessage "Failed to export database from Docker mysql."

    Write-Host "Copying dump to: $DumpPath"
    Invoke-Native -FilePath "docker" -Arguments @("cp", "${ContainerName}:$RemoteDumpPath", $DumpPath) -FailureMessage "Failed to copy dump file from Docker container."
  } finally {
    Pop-Location
  }
}

Write-Host "Dump file ready: $DumpPath"

if ($ExportOnly) {
  Write-Host "ExportOnly is set. Local import skipped."
  exit 0
}

if (-not $Force) {
  Write-Host ""
  Write-Host "Importing this dump may replace tables in local database '$Database' on ${LocalHostName}:$LocalPort."
  $Answer = Read-Host "Type IMPORT to continue"
  if ($Answer -ne "IMPORT") {
    throw "Import cancelled."
  }
}

$MysqlRootArgs = @(
  "--protocol=tcp",
  "-h", $LocalHostName,
  "-P", "$LocalPort",
  "-u", $LocalRootUser
)

if ($LocalRootPassword) {
  $MysqlRootArgs += "-p$LocalRootPassword"
} else {
  $MysqlRootArgs += "-p"
}

$AppUserSql = Escape-SqlLiteral $AppUser
$AppPasswordSql = Escape-SqlLiteral $AppPassword
$SetupSql = "CREATE DATABASE IF NOT EXISTS $Database DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; CREATE USER IF NOT EXISTS '$AppUserSql'@'localhost' IDENTIFIED BY '$AppPasswordSql'; CREATE USER IF NOT EXISTS '$AppUserSql'@'127.0.0.1' IDENTIFIED BY '$AppPasswordSql'; ALTER USER '$AppUserSql'@'localhost' IDENTIFIED BY '$AppPasswordSql'; ALTER USER '$AppUserSql'@'127.0.0.1' IDENTIFIED BY '$AppPasswordSql'; GRANT ALL PRIVILEGES ON $Database.* TO '$AppUserSql'@'localhost'; GRANT ALL PRIVILEGES ON $Database.* TO '$AppUserSql'@'127.0.0.1'; FLUSH PRIVILEGES;"

Write-Host "Preparing local database and user..."
Invoke-Native -FilePath "mysql" -Arguments ($MysqlRootArgs + @("-e", $SetupSql)) -FailureMessage "Failed to prepare local database/user."

$MysqlSourcePath = ($DumpPath -replace "\\", "/")
Write-Host "Importing dump into local database '$Database'..."
Invoke-Native -FilePath "mysql" -Arguments ($MysqlRootArgs + @("--default-character-set=utf8mb4", "--binary-mode=1", $Database, "-e", "SOURCE $MysqlSourcePath")) -FailureMessage "Failed to import dump into local mysql."

Write-Host "Verifying local tables..."
Invoke-Native -FilePath "mysql" -Arguments ($MysqlRootArgs + @($Database, "-e", "SHOW TABLES;")) -FailureMessage "Failed to verify local mysql tables."

Write-Host "Migration finished."
