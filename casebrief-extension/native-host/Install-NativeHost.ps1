param(
    [Parameter(Mandatory = $true)]
    [string]$HostExecutable,
    [string]$ExtensionId = 'kgblcnakcckleijbjknldipphmkficma'
)

$ErrorActionPreference = 'Stop'
$hostName = 'com.casebrief.native'

if ($ExtensionId -notmatch '^[a-p]{32}$') {
    throw 'ExtensionId must be the exact 32-character Chrome extension ID.'
}
if (-not (Test-Path -LiteralPath $HostExecutable -PathType Leaf)) {
    throw "Native host executable was not found: $HostExecutable"
}

$resolvedExecutable = (Resolve-Path -LiteralPath $HostExecutable).Path
$manifestPath = Join-Path (Split-Path -Parent $resolvedExecutable) 'com.casebrief.native.json'
$manifest = [ordered]@{
    name = $hostName
    description = 'CaseBrief local document processing host'
    path = $resolvedExecutable
    type = 'stdio'
    allowed_origins = @("chrome-extension://$ExtensionId/")
}
$json = $manifest | ConvertTo-Json -Depth 4
[System.IO.File]::WriteAllText($manifestPath, $json, [System.Text.UTF8Encoding]::new($false))

$registryPath = "HKCU:\Software\Google\Chrome\NativeMessagingHosts\$hostName"
New-Item -Path $registryPath -Force | Out-Null
Set-Item -Path $registryPath -Value $manifestPath
Write-Output "Registered $hostName for extension $ExtensionId"
Write-Output "Manifest: $manifestPath"
