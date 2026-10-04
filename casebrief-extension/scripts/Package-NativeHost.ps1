$ErrorActionPreference = 'Stop'
$extensionRoot = Split-Path -Parent $PSScriptRoot
$workspaceRoot = Split-Path -Parent $extensionRoot
$targetDir = Join-Path $workspaceRoot 'target'
$stageDir = Join-Path $targetDir 'native-host-input'
$distDir = Join-Path $targetDir 'native-host-dist'
$imageDir = Join-Path $distDir 'CaseBriefNativeHost'

$maven = Get-Command mvn -ErrorAction Stop
$jpackage = Get-Command jpackage -ErrorAction Stop
& $maven.Source -f (Join-Path $workspaceRoot 'pom.xml') -DskipTests package
if ($LASTEXITCODE -ne 0) { throw 'Maven package failed.' }

$bootJar = Get-ChildItem -Path $targetDir -Filter 'casebrief-*.jar' -File |
    Where-Object { $_.Name -notmatch '\.original$' } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if (-not $bootJar) { throw 'Spring Boot executable JAR was not found in target/.' }

New-Item -ItemType Directory -Force -Path $stageDir, $distDir | Out-Null
Copy-Item -LiteralPath $bootJar.FullName -Destination (Join-Path $stageDir 'casebrief.jar') -Force
if (Test-Path -LiteralPath $imageDir) { Remove-Item -LiteralPath $imageDir -Recurse -Force }

& $jpackage.Source `
    --type app-image `
    --name CaseBriefNativeHost `
    --app-version 1.0.0 `
    --input $stageDir `
    --main-jar casebrief.jar `
    --main-class org.springframework.boot.loader.launch.PropertiesLauncher `
    --java-options '-Dloader.main=com.casebrief.nativehost.NativeMessagingHost' `
    --win-console `
    --dest $distDir
if ($LASTEXITCODE -ne 0) { throw 'jpackage failed to create the bundled-runtime app image.' }

Write-Output "Native host app image: $imageDir"
Write-Output 'This image contains its runtime. Use the Inno Setup project to create the per-user installer.'
