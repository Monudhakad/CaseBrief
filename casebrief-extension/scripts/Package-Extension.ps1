$ErrorActionPreference = 'Stop'
$extensionRoot = Split-Path -Parent $PSScriptRoot
$workspaceRoot = Split-Path -Parent $extensionRoot
$stageDir = Join-Path $workspaceRoot 'target\casebrief-extension-stage'
$distDir = Join-Path $workspaceRoot 'dist'
$archive = Join-Path $distDir 'casebrief-extension.zip'

if (Test-Path -LiteralPath $stageDir) { Remove-Item -LiteralPath $stageDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path $stageDir, $distDir | Out-Null
Copy-Item (Join-Path $extensionRoot 'manifest.json') $stageDir
Copy-Item (Join-Path $extensionRoot 'service-worker.js') $stageDir
Copy-Item (Join-Path $extensionRoot 'popup') $stageDir -Recurse
Copy-Item (Join-Path $extensionRoot 'assets') $stageDir -Recurse
if (Test-Path -LiteralPath $archive) { Remove-Item -LiteralPath $archive -Force }
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open($archive, [System.IO.Compression.ZipArchiveMode]::Create)
try {
	Get-ChildItem -LiteralPath $stageDir -File -Recurse | ForEach-Object {
		$relativePath = $_.FullName.Substring($stageDir.Length).TrimStart([char[]]@('\', '/')).Replace('\', '/')
		$entry = $zip.CreateEntry($relativePath, [System.IO.Compression.CompressionLevel]::Optimal)
		$entryStream = $entry.Open()
		$fileStream = [System.IO.File]::OpenRead($_.FullName)
		try { $fileStream.CopyTo($entryStream) }
		finally { $fileStream.Dispose(); $entryStream.Dispose() }
	}
}
finally {
	$zip.Dispose()
}
Write-Output "Extension package: $archive"
