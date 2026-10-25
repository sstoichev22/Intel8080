# Builds build/intel8080.jar. Run a program: java -jar build\intel8080.jar path\to\file.asm [instructionLimit]
$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$jdk = Get-ChildItem -LiteralPath (Join-Path $env:ProgramFiles 'Java') -Directory -Filter 'jdk*' -ErrorAction SilentlyContinue |
    Where-Object { Test-Path (Join-Path $_.FullName 'bin\javac.exe') } | Select-Object -Last 1
if ($jdk) { $bin = Join-Path $jdk.FullName 'bin'; $javac = "$bin\javac.exe"; $jar = "$bin\jar.exe" }
else { $javac = (Get-Command javac).Source; $jar = (Get-Command jar).Source }
$classes = Join-Path $here 'build\classes'
if (Test-Path $classes) { Remove-Item $classes -Recurse -Force }
New-Item -ItemType Directory -Force $classes | Out-Null
$sources = Get-ChildItem (Join-Path $here 'src') -Recurse -Filter *.java | ForEach-Object FullName
& $javac --release 17 -encoding UTF-8 -d $classes @sources
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed.' }
& $jar --create --file (Join-Path $here 'build\intel8080.jar') --main-class Intel8080.main.Main -C $classes .
if ($LASTEXITCODE -ne 0) { throw 'Packaging failed.' }
'Created build\intel8080.jar'
