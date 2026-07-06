$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$env:MAVEN_OPTS = '-Dmaven.repo.local=d:/code/lontri/questioncode/.m2/repository'
$env:SPRING_DATASOURCE_URL = 'jdbc:mysql://localhost:3306/problem_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true'
$env:SPRING_DATASOURCE_USERNAME = 'lontri'
if (-not $env:SPRING_DATASOURCE_PASSWORD) {
  $env:SPRING_DATASOURCE_PASSWORD = ''
}

$mvn = 'D:\Program Files\Apache\Maven\bin\mvn.cmd'
if (!(Test-Path $mvn)) {
  $mvn = 'mvn'
}

if (Test-Path 'target\classes') {
  Remove-Item -Recurse -Force 'target\classes'
}

& $mvn -DskipTests "-Djava.version=17" spring-boot:run *> .backend-admin-restart.log
