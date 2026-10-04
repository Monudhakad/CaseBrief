
$ErrorActionPreference = 'Stop'

$extensionRoot = Split-Path -Parent $PSScriptRoot

# Step 1: Build the native host
& (Join-Path $PSScriptRoot 'Package-NativeHost.ps1')

# Step 2: Package the Chrome extension
& (Join-Path $PSScriptRoot 'Package-Extension.ps1')

# Step 3: Locate Inno Setup Compiler
$compilerPath = (Get-Command ISCC.exe -ErrorAction SilentlyContinue).Source

if (-not $compilerPath) {
    $knownPaths = @(
        "$env:LOCALAPPDATA\Programs\Inno Setup 6\ISCC.exe",
        "$env:ProgramFiles(x86)\Inno Setup 6\ISCC.exe",
        "$env:ProgramFiles\Inno Setup 6\ISCC.exe"
    )

    foreach ($path in $knownPaths) {
        if (Test-Path -LiteralPath $path) {
            $compilerPath = $path
            break
        }
    }
}

# Step 4: Skip installer compilation if unavailable
if (-not $compilerPath) {
    Write-Warning 'Inno Setup not found. Native host and extension packages are ready, but the installer was not compiled.'
    return
}

# Step 5: Compile the Windows installer
$installerScript = Join-Path $extensionRoot 'installer\CaseBrief.iss'

& $compilerPath $installerScript

if ($LASTEXITCODE -ne 0) {
    throw 'Inno Setup failed to compile the host installer.'
}

Write-Output 'Release artifacts are ready in dist\.'
