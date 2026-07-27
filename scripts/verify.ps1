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

& git diff --exit-code -- .
if ($LASTEXITCODE -ne 0)
{
    throw 'Verification changed tracked files.'
}
