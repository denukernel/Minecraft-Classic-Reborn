param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$MavenRepository = (Join-Path $env:USERPROFILE '.m2/repository')
)

# Offline JDK build, including dependencies that Maven system scope would omit.
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
if ($JavaHome) {
    $jdkBin = Join-Path $JavaHome 'bin'
} else {
    $jdkBin = Split-Path (Get-Command javac -ErrorAction Stop).Source -Parent
    # Oracle javapath does not include the jar tool. Resolve the actual JDK home.
    if (!(Test-Path (Join-Path $jdkBin 'jar.exe'))) {
        $settings = & (Join-Path $jdkBin 'java.exe') -XshowSettings:properties -version 2>&1
        $homeLine = $settings | Select-String '^\s*java.home\s*=\s*(.+)$' | Select-Object -First 1
        if (!$homeLine) { throw 'cannot locate the jdk; supply -JavaHome' }
        $jdkBin = Join-Path $homeLine.Matches[0].Groups[1].Value.Trim() 'bin'
    }
}
$compiler = Join-Path $jdkBin 'javac.exe'
$archiver = Join-Path $jdkBin 'jar.exe'
foreach ($tool in @($compiler, $archiver)) {
    if (!(Test-Path -LiteralPath $tool)) { throw "missing jdk tool: $tool" }
}
$dependencyPaths = @(
    'org/lwjgl/lwjgl/lwjgl/2.8.4/lwjgl-2.8.4.jar',
    'org/lwjgl/lwjgl/lwjgl_util/2.8.4/lwjgl_util-2.8.4.jar',
    'org/lwjgl/lwjgl/lwjgl_util_applet/2.8.4/lwjgl_util_applet-2.8.4.jar',
    'net/java/jinput/jinput/2.0.4/jinput-2.0.4.jar',
    'net/java/jutils/jutils/1.0.0/jutils-1.0.0.jar',
    'org/eclipse/platform/org.eclipse.swt.win32.win32.x86_64/3.130.0/org.eclipse.swt.win32.win32.x86_64-3.130.0.jar'
)
$dependencies = @($dependencyPaths | ForEach-Object { Join-Path $MavenRepository $_ })
$dependencies += @(Get-ChildItem (Join-Path $projectRoot 'libjar') -Filter '*.jar' | ForEach-Object FullName)
foreach ($dependency in $dependencies) {
    if (!(Test-Path -LiteralPath $dependency)) { throw "missing cached dependency: $dependency" }
}

function Assert-RunnableJar([string]$Path, [string]$MainClass, [string[]]$RequiredEntries) {
    $jar = [System.IO.Compression.ZipFile]::OpenRead($Path)
    try {
        foreach ($required in @('META-INF/MANIFEST.MF', ($MainClass.Replace('.', '/') + '.class')) + $RequiredEntries) {
            if (!$jar.GetEntry($required)) { throw "jar entry missing in ${Path}: $required" }
        }
        $reader = [System.IO.StreamReader]::new($jar.GetEntry('META-INF/MANIFEST.MF').Open())
        try { $manifest = $reader.ReadToEnd() } finally { $reader.Dispose() }
        $unfoldedManifest = $manifest -replace "\r?\n ", ''
        if (($unfoldedManifest -split '\r?\n') -cnotcontains "Main-Class: $MainClass") {
            throw "incorrect packaged entry point in $Path"
        }
    } finally { $jar.Dispose() }
}

Push-Location $projectRoot
try {
    $target = Join-Path $projectRoot 'target'
    New-Item -ItemType Directory -Force $target | Out-Null
    # A fresh staging directory prevents removed classes from surviving a rebuild.
    $stage = Join-Path $target ('jar-build-' + [Guid]::NewGuid().ToString('N'))
    $content = Join-Path $stage 'content'
    New-Item -ItemType Directory -Force $content | Out-Null
    $utf8 = [System.Text.UTF8Encoding]::new($false)
    $sourceList = Join-Path $stage 'sources.txt'
    $sourceLines = @(Get-ChildItem 'src/main/java' -Recurse -Filter '*.java' |
        ForEach-Object { '"' + $_.FullName.Replace('\', '/') + '"' })
    [System.IO.File]::WriteAllLines($sourceList, $sourceLines, $utf8)
    $classpath = $dependencies -join ';'
    $compileLog = Join-Path $stage 'compile.log'
    & $compiler --release 16 -proc:none -encoding UTF-8 -cp $classpath -d $content "@$sourceList" *> $compileLog
    if ($LASTEXITCODE -ne 0) {
        Get-Content -LiteralPath $compileLog
        throw 'game compilation failed'
    }

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $services = @{}
    $contentPrefix = [System.IO.Path]::GetFullPath($content) + [System.IO.Path]::DirectorySeparatorChar
    foreach ($dependency in $dependencies) {
        $archive = [System.IO.Compression.ZipFile]::OpenRead($dependency)
        try {
            foreach ($entry in $archive.Entries) {
                $name = $entry.FullName
                if ($name.EndsWith('/') -or $name -match '(?i)^META-INF/(MANIFEST\.MF|INDEX\.LIST)$|^META-INF/[^/]+\.(SF|RSA|DSA|EC)$|(^|/)module-info\.class$|\.java$') { continue }
                if ($name.StartsWith('META-INF/services/')) {
                    if (!$services.ContainsKey($name)) { $services[$name] = [System.Collections.Generic.HashSet[string]]::new() }
                    $reader = [System.IO.StreamReader]::new($entry.Open())
                    try {
                        while (!$reader.EndOfStream) {
                            $line = ($reader.ReadLine() -split '#', 2)[0].Trim()
                            if ($line) { [void]$services[$name].Add($line) }
                        }
                    } finally { $reader.Dispose() }
                    continue
                }
                $destination = [System.IO.Path]::GetFullPath((Join-Path $content $name))
                if (!$destination.StartsWith($contentPrefix, [StringComparison]::OrdinalIgnoreCase)) { throw "invalid archive entry: $name" }
                # Project classes take precedence over any bundled dependency copies.
                if (Test-Path -LiteralPath $destination) { continue }
                [void][System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($destination))
                [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $destination)
            }
        } finally { $archive.Dispose() }
    }
    foreach ($service in $services.Keys) {
        $destination = Join-Path $content $service
        [void][System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($destination))
        [System.IO.File]::WriteAllLines($destination, [string[]]$services[$service], $utf8)
    }
    Copy-Item 'src/main/resources/*' -Destination $content -Recurse -Force
    [xml]$pom = Get-Content 'pom.xml' -Raw
    $entryPoints = @($pom.SelectNodes("//*[local-name()='mainClass']") | ForEach-Object { $_.InnerText.Trim() } | Select-Object -Unique)
    if ($entryPoints.Count -ne 1) { throw 'jar and shade entry points must agree' }
    $entryPoint = $entryPoints[0]
    $candidate = Join-Path $stage 'game.jar'
    $releaseBuild = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    $launcherManifest = Join-Path $stage 'launcher.mf'
    [System.IO.File]::WriteAllText($launcherManifest, "Classic-Reborn-Build: $releaseBuild`r`n", $utf8)
    & $archiver --create --file $candidate --manifest $launcherManifest --main-class $entryPoint -C $content .
    if ($LASTEXITCODE -ne 0) { throw 'jar packaging failed' }

    $serverCandidate = Join-Path $stage 'server.jar'
    $serverEntryPoint = 'net.classicremastered.toolkit.server.ClassicServer'
    & $archiver --create --file $serverCandidate --main-class $serverEntryPoint -C $content .
    if ($LASTEXITCODE -ne 0) { throw 'server creator jar packaging failed' }

    Assert-RunnableJar $candidate $entryPoint @(
        'textures/block/tiles.properties', 'textures/entity/chest.png', 'org/lwjgl/opengl/GL11.class', 'net/java/games/input/Controller.class',
        'net/java/games/util/plugins/Plugin.class', 'paulscode/sound/SoundSystem.class',
        'paulscode/sound/codecs/CodecJOrbis.class', 'net/classicremastered/launcher/LauncherUpdater.class',
        'sound/item/gravity_gun/grab.wav', 'sound/item/gravity_gun/drop.wav', 'sound/item/gravity_gun/launch.wav'
    )
    Assert-RunnableJar $serverCandidate $serverEntryPoint @()
    $serverArtifact = Join-Path $target 'ClassicReborn-Server-Creator.jar'
    Move-Item -LiteralPath $serverCandidate -Destination $serverArtifact -Force

    $artifact = Join-Path $target "ClassicRemastered-$($pom.project.version)-all.jar"
    Move-Item -LiteralPath $candidate -Destination $artifact -Force
    $artifactHash = Get-FileHash -LiteralPath $artifact -Algorithm SHA256
    $artifactHash | Format-List Path, Hash
    $release = Join-Path $target 'launcher-release.txt'
    [System.IO.File]::WriteAllText($release, "$releaseBuild $($artifactHash.Hash.ToLowerInvariant())`n", $utf8)
    Write-Output "built and verified: $artifact"
} finally {
    # Delete only the fresh staging directory directly beneath this project target.
    if ($stage -and (Test-Path -LiteralPath $stage)) {
        $resolvedStage = [System.IO.Path]::GetFullPath($stage)
        $resolvedTarget = [System.IO.Path]::GetFullPath((Join-Path $projectRoot 'target'))
        if ([System.IO.Path]::GetDirectoryName($resolvedStage) -ne $resolvedTarget -or
            [System.IO.Path]::GetFileName($resolvedStage) -notmatch '^jar-build-[0-9a-f]{32}$') {
            throw "refusing to remove unexpected staging path: $resolvedStage"
        }
        Remove-Item -LiteralPath $resolvedStage -Recurse -Force
    }
    Pop-Location
}
