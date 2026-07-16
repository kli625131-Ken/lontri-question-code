param(
  [int]$MysqlPort = 3307,
  [int]$BackendPort = 8080,
  [string]$Database = "problem_db",
  [string]$Username = "lontri",
  [string]$Password = $env:SPRING_DATASOURCE_PASSWORD,
  [switch]$ImportBootstrap
)

$ErrorActionPreference = "Stop"

$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
$BackendDir = Join-Path $Root "backend"
$EnvFile = Join-Path $Root ".env"

if (Test-Path $EnvFile) {
  Get-Content $EnvFile | ForEach-Object {
    $Line = $_.Trim()
    if (-not $Line -or $Line.StartsWith("#") -or -not $Line.Contains("=")) {
      return
    }
    $Parts = $Line.Split("=", 2)
    $Name = $Parts[0].Trim()
    $Value = $Parts[1].Trim()
    if ($Name -and -not [Environment]::GetEnvironmentVariable($Name, "Process")) {
      [Environment]::SetEnvironmentVariable($Name, $Value, "Process")
    }
  }
}

if (-not $Password) {
  $Password = $env:MYSQL_PASSWORD
}

if (-not $Password) {
  $Password = "change_me_app_password"
}

$env:SPRING_PROFILES_ACTIVE = "dev"
$env:SERVER_PORT = "$BackendPort"
$env:SPRING_DATASOURCE_URL = "jdbc:mysql://localhost:${MysqlPort}/${Database}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
$env:SPRING_DATASOURCE_USERNAME = $Username
$env:SPRING_DATASOURCE_PASSWORD = $Password

if (-not $ImportBootstrap) {
  $env:OPS_IMPORT_BOOTSTRAP_ENABLED = "false"
}

Set-Location $BackendDir
mvn clean spring-boot:run "-Dspring-boot.run.profiles=dev"
