param(
    [string] $Abi = 'arm64-v8a',
    [string] $RimeSource = '.downloads/librime-1.16.1',
    [string] $BoostSource = '.downloads/boost-1.89.0'
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$nativeSource = Join-Path $projectRoot 'native/librime'
$rime = (Resolve-Path (Join-Path $projectRoot $RimeSource)).Path
$boost = (Resolve-Path (Join-Path $projectRoot $BoostSource)).Path
$ndk = Join-Path $projectRoot '.android-sdk/ndk/28.0.13004108'
$cmake = Join-Path $projectRoot '.android-sdk/cmake/3.31.6/bin/cmake.exe'
$ninja = Join-Path $projectRoot '.android-sdk/cmake/3.31.6/bin/ninja.exe'
$strip = Join-Path $ndk 'toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-strip.exe'
$buildDirectory = Join-Path $projectRoot ".native-build/$Abi"
$outputDirectory = Join-Path $projectRoot "app/src/main/jniLibs/$Abi"

New-Item -ItemType Directory -Force -Path $buildDirectory, $outputDirectory | Out-Null
& $cmake -S $nativeSource -B $buildDirectory -G Ninja `
    "-DCMAKE_MAKE_PROGRAM=$ninja" `
    "-DCMAKE_TOOLCHAIN_FILE=$(Join-Path $ndk 'build/cmake/android.toolchain.cmake')" `
    "-DANDROID_ABI=$Abi" `
    '-DANDROID_PLATFORM=android-23' `
    '-DANDROID_STL=c++_static' `
    '-DCMAKE_BUILD_TYPE=Release' `
    "-DRIME_SOURCE=$rime" `
    "-DBOOST_SOURCE=$boost"
if ($LASTEXITCODE -ne 0) { throw 'CMake configuration failed' }
& $cmake --build $buildDirectory --target ziyu_rime --parallel
if ($LASTEXITCODE -ne 0) { throw 'Native build failed' }
Copy-Item -LiteralPath (Join-Path $buildDirectory 'libziyu_rime.so') `
    -Destination (Join-Path $outputDirectory 'libziyu_rime.so') -Force
& $strip --strip-unneeded (Join-Path $outputDirectory 'libziyu_rime.so')
if ($LASTEXITCODE -ne 0) { throw 'Native strip failed' }
