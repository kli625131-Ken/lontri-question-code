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
