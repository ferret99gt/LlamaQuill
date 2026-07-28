[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
Set-Location -LiteralPath $repositoryRoot

function Invoke-Maven
{
    param([Parameter(Mandatory)][string[]]$Arguments)

    & mvn @Arguments
    if ($LASTEXITCODE -ne 0)
    {
        throw "Maven failed: mvn $($Arguments -join ' ')"
    }
}

$bomFiles = @()
$trackedFileHashes = @{}
$trackedFiles = & git ls-files
if ($LASTEXITCODE -ne 0)
{
    throw 'Unable to list tracked files.'
}

foreach ($relativePath in $trackedFiles)
{
    $path = Join-Path $repositoryRoot $relativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf))
    {
        continue
    }

    $bytes = [System.IO.File]::ReadAllBytes($path)
    $trackedFileHashes[$relativePath] = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash
    if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF)
    {
        $bomFiles += $relativePath
    }
}

if ($bomFiles.Count -gt 0)
{
    throw "UTF-8 BOMs are not allowed:`n$($bomFiles -join [Environment]::NewLine)"
}

Invoke-Maven @('clean', 'verify')
Invoke-Maven @('-Prelease', 'package')
Invoke-Maven @('clean')
Invoke-Maven @('-Prelease', 'package')

$launcher = Join-Path $repositoryRoot 'target\dist\LlamaQuill\LlamaQuill.exe'
$runtimeJava = Join-Path $repositoryRoot 'target\dist\LlamaQuill\runtime\bin\java.exe'
if (-not (Test-Path -LiteralPath $launcher -PathType Leaf) -or
    -not (Test-Path -LiteralPath $runtimeJava -PathType Leaf))
{
    throw 'Packaged launcher or runtime is missing.'
}

$runtimeVersion = (& $runtimeJava --version 2>&1) -join [Environment]::NewLine
if ($LASTEXITCODE -ne 0 -or $runtimeVersion -notmatch '\b25\.0\.4\b')
{
    throw "Packaged runtime is not OpenJDK 25.0.4:`n$runtimeVersion"
}

[xml]$pom = Get-Content -LiteralPath (Join-Path $repositoryRoot 'pom.xml') -Raw
$version = [string]$pom.project.version
$versionSource = Get-Content -LiteralPath (
        Join-Path $repositoryRoot 'src\main\java\com\llamaquill\AppVersion.java') -Raw
$escapedVersion = [regex]::Escape($version)
if ($versionSource -notmatch "CURRENT\s*=\s*`"$escapedVersion`"")
{
    throw "AppVersion.CURRENT does not match pom.xml version $version."
}

$smokeDirectory = [System.IO.Path]::GetFullPath((
        Join-Path $repositoryRoot "target\smoke-$([guid]::NewGuid().ToString('N'))"))
$targetRoot = [System.IO.Path]::GetFullPath((Join-Path $repositoryRoot 'target'))
if (-not $smokeDirectory.StartsWith($targetRoot + [System.IO.Path]::DirectorySeparatorChar,
        [System.StringComparison]::OrdinalIgnoreCase))
{
    throw "Refusing to use a smoke-test directory outside target: $smokeDirectory"
}

$previousDataDirectory = [Environment]::GetEnvironmentVariable('LLAMAQUILL_DATA_DIR', 'Process')
try
{
    [Environment]::SetEnvironmentVariable('LLAMAQUILL_DATA_DIR', $smokeDirectory, 'Process')
    $smokeProcess = Start-Process -FilePath $launcher -ArgumentList '--smoke-test' -PassThru -Wait
    if ($smokeProcess.ExitCode -ne 0)
    {
        throw "Packaged launcher smoke test exited with code $($smokeProcess.ExitCode)."
    }
    if (-not (Test-Path -LiteralPath (Join-Path $smokeDirectory 'llamaquill.db') -PathType Leaf))
    {
        throw 'Packaged launcher smoke test did not create its isolated database.'
    }
}
finally
{
    [Environment]::SetEnvironmentVariable('LLAMAQUILL_DATA_DIR', $previousDataDirectory, 'Process')
    if (Test-Path -LiteralPath $smokeDirectory)
    {
        Remove-Item -LiteralPath $smokeDirectory -Recurse -Force
    }
}

& (Join-Path $PSScriptRoot 'package-release.ps1') -SkipBuild
if ($LASTEXITCODE -ne 0)
{
    throw 'Release archive creation failed.'
}

$archivePath = Join-Path $repositoryRoot "target\release\LlamaQuill-$version-windows-x64.zip"
$checksumPath = "$archivePath.sha256"
if (-not (Test-Path -LiteralPath $archivePath -PathType Leaf) -or
    -not (Test-Path -LiteralPath $checksumPath -PathType Leaf))
{
    throw 'The release ZIP or its SHA-256 checksum is missing.'
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::OpenRead($archivePath)
try
{
    $entryNames = @($archive.Entries | ForEach-Object { $_.FullName.Replace('\', '/') })
    foreach ($requiredEntry in @(
        'LlamaQuill/LlamaQuill.exe',
        'LlamaQuill/runtime/bin/java.exe',
        'LlamaQuill/LICENSE',
        'LlamaQuill/README.md',
        'LlamaQuill/CHANGELOG.md'
    ))
    {
        if ($entryNames -notcontains $requiredEntry)
        {
            throw "Release ZIP is missing $requiredEntry."
        }
    }

    if (-not ($entryNames | Where-Object { $_ -match '^LlamaQuill/app/LlamaQuill[^/]*\.jar$' }))
    {
        throw 'Release ZIP is missing the LlamaQuill application JAR.'
    }
}
finally
{
    $archive.Dispose()
}

$trackedFilesAfter = @(& git ls-files)
if ($LASTEXITCODE -ne 0 -or
    (Compare-Object -ReferenceObject @($trackedFiles) -DifferenceObject $trackedFilesAfter))
{
    throw 'Verification changed the set of tracked files.'
}

$changedByVerification = @()
foreach ($relativePath in $trackedFilesAfter)
{
    $path = Join-Path $repositoryRoot $relativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf))
    {
        $changedByVerification += $relativePath
        continue
    }
    $currentHash = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash
    if ($currentHash -ne $trackedFileHashes[$relativePath])
    {
        $changedByVerification += $relativePath
    }
}

if ($changedByVerification.Count -gt 0)
{
    throw "Verification changed tracked files:`n$($changedByVerification -join [Environment]::NewLine)"
}
