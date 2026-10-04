$ErrorActionPreference = 'Stop'
$hostName = 'com.casebrief.native'
$registryPath = "HKCU:\Software\Google\Chrome\NativeMessagingHosts\$hostName"

if (Test-Path -LiteralPath $registryPath) {
    $manifestPath = (Get-Item -LiteralPath $registryPath).GetValue('')
    Remove-Item -LiteralPath $registryPath -Recurse -Force
    if ($manifestPath -and (Test-Path -LiteralPath $manifestPath)) {
        Remove-Item -LiteralPath $manifestPath -Force
    }
    Write-Output "Unregistered $hostName"
} else {
    Write-Output "$hostName was not registered for the current user"
}
