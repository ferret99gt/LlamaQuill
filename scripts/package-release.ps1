[CmdletBinding()]
param(
    [switch]$SkipBuild
)

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

if (-not $SkipBuild)
{
    Invoke-Maven @('clean', '-DskipTests', '-Prelease', 'package')
}

[xml]$pom = Get-Content -LiteralPath (Join-Path $repositoryRoot 'pom.xml') -Raw
$version = [string]$pom.project.version
if ([string]::IsNullOrWhiteSpace($version))
{
    throw 'Unable to read the project version from pom.xml.'
}

$appImage = Join-Path $repositoryRoot 'target\dist\LlamaQuill'
$launcher = Join-Path $appImage 'LlamaQuill.exe'
$appDirectory = Join-Path $appImage 'app'
$runtimeJava = Join-Path $appImage 'runtime\bin\java.exe'
if (-not (Test-Path -LiteralPath $launcher -PathType Leaf) -or
    -not (Test-Path -LiteralPath $appDirectory -PathType Container) -or
    -not (Test-Path -LiteralPath $runtimeJava -PathType Leaf))
{
    throw "The complete LlamaQuill app image was not found at $appImage."
}

$appJar = Get-ChildItem -LiteralPath $appDirectory -Filter 'LlamaQuill*.jar' -File
if ($appJar.Count -ne 1)
{
    throw "Expected exactly one LlamaQuill application JAR under $appDirectory; found $($appJar.Count)."
}

foreach ($releaseDocument in @('LICENSE', 'README.md', 'CHANGELOG.md'))
{
    $sourceDocument = Join-Path $repositoryRoot $releaseDocument
    if (-not (Test-Path -LiteralPath $sourceDocument -PathType Leaf))
    {
        throw "Required release document is missing: $sourceDocument"
    }
    Copy-Item -LiteralPath $sourceDocument -Destination (Join-Path $appImage $releaseDocument) -Force
}

$releaseDirectory = [System.IO.Path]::GetFullPath((Join-Path $repositoryRoot 'target\release'))
$targetRoot = [System.IO.Path]::GetFullPath((Join-Path $repositoryRoot 'target'))
if (-not $releaseDirectory.StartsWith($targetRoot + [System.IO.Path]::DirectorySeparatorChar,
        [System.StringComparison]::OrdinalIgnoreCase))
{
    throw "Refusing to clean release output outside the target directory: $releaseDirectory"
}

if (Test-Path -LiteralPath $releaseDirectory)
{
    Remove-Item -LiteralPath $releaseDirectory -Recurse -Force
}
New-Item -ItemType Directory -Path $releaseDirectory | Out-Null

$archiveName = "LlamaQuill-$version-windows-x64.zip"
$archivePath = Join-Path $releaseDirectory $archiveName
Compress-Archive -LiteralPath $appImage -DestinationPath $archivePath -CompressionLevel Optimal

$checksum = (Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash.ToLowerInvariant()
$checksumPath = "$archivePath.sha256"
[System.IO.File]::WriteAllText(
    $checksumPath,
    "$checksum *$archiveName$([Environment]::NewLine)",
    [System.Text.UTF8Encoding]::new($false)
)

Write-Host "Release archive: $archivePath"
Write-Host "SHA-256:        $checksum"
