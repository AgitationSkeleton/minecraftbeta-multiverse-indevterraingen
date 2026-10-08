param(
    [Parameter(Mandatory=$true)][string]$ServerJar
)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$build = Join-Path $root 'build'
$dist = Join-Path $root 'dist'
Remove-Item -LiteralPath $build -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $build,$dist | Out-Null

function Build-Plugin([string]$module, [string]$mainClass, [string]$jarName) {
    $classes = Join-Path $build $module
    New-Item -ItemType Directory -Force $classes | Out-Null
    javac --release 8 -classpath $ServerJar -d $classes (Join-Path $root "$module\src\main\java\$mainClass.java")
    Copy-Item (Join-Path $root "$module\src\main\resources\plugin.yml") (Join-Path $classes 'plugin.yml')
    jar cf (Join-Path $dist $jarName) -C $classes .
}

$bridgeClasses = Join-Path $build 'bridge'
New-Item -ItemType Directory -Force $bridgeClasses | Out-Null
javac --release 8 -d $bridgeClasses (Join-Path $root 'bridge\src\main\java\IndevGeneratorBridge.java')
jar cf (Join-Path $dist 'IndevGeneratorBridge.jar') -C $bridgeClasses .
Build-Plugin 'generator' 'IndevWorldGen' 'IndevWorldGen.jar'
Build-Plugin 'mob-rules' 'IndevMobRules' 'IndevMobRules.jar'

Get-ChildItem $dist

