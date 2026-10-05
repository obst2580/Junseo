# Downloads WorldEdit for Paper from Modrinth into the plugins folder.
# start.bat runs this automatically when plugins has no worldedit*.jar.
# Manual run:  powershell -NoProfile -ExecutionPolicy Bypass -File get-worldedit.ps1 [-GameVersion 26.2]
param([string]$GameVersion = "26.2")
$ErrorActionPreference = "Stop"
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$headers = @{ "User-Agent" = "JunseoCity-server-kit (github.com/obst2580/Junseo)" }
Set-Location -LiteralPath $PSScriptRoot
if (-not (Test-Path plugins)) { New-Item -ItemType Directory plugins | Out-Null }

function Get-WorldEditVersions([string]$gv) {
    $url = "https://api.modrinth.com/v2/project/worldedit/version?loaders=" + [uri]::EscapeDataString('["paper","bukkit"]')
    if ($gv) { $url += "&game_versions=" + [uri]::EscapeDataString('["' + $gv + '"]') }
    return @(Invoke-RestMethod -Uri $url -Headers $headers)
}

try {
    $versions = Get-WorldEditVersions $GameVersion
    if ($versions.Count -eq 0) {
        Write-Host "[WorldEdit] No build marked for Minecraft $GameVersion yet. Trying the newest build."
        $versions = Get-WorldEditVersions ""
    }
    if ($versions.Count -eq 0) { throw "no WorldEdit versions found" }
    $v = $versions[0]
    $file = $v.files | Where-Object { $_.primary } | Select-Object -First 1
    if (-not $file) { $file = $v.files[0] }
    $out = Join-Path "plugins" $file.filename
    Write-Host "[WorldEdit] Downloading $($v.version_number) ($($file.filename)) ..."
    Invoke-WebRequest -Uri $file.url -OutFile $out -Headers $headers -UseBasicParsing
    Write-Host "[WorldEdit] Saved to $out"
} catch {
    Write-Host "[WorldEdit] Download failed: $($_.Exception.Message)"
    Write-Host "[WorldEdit] Get it by hand: https://modrinth.com/plugin/worldedit  (Versions -> Paper -> your Minecraft version)"
    Write-Host "[WorldEdit] and put the .jar file in the plugins folder. The server will start without it."
}
