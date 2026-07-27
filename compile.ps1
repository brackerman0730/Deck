# ============================================================================
# Deck - compile script
# ----------------------------------------------------------------------------
# Compiles all Java sources under src\ into out\.
# Requires JavaFX SDK at C:\javafx-sdk-26.0.1\lib and sqlite-jdbc in lib\.
# ============================================================================

$ErrorActionPreference = "Stop"

$javafxLib = "C:\javafx-sdk-26.0.1\lib"
$sqliteJar = "lib\sqlite-jdbc-3.53.2.0.jar"
$srcRoot   = "src"
$outDir    = "out"

if (-not (Test-Path $javafxLib)) {
    Write-Host "JavaFX SDK not found at $javafxLib" -ForegroundColor Red
    exit 1
}
if (-not (Test-Path $sqliteJar)) {
    Write-Host "SQLite JAR not found at $sqliteJar" -ForegroundColor Red
    exit 1
}

# Collect all .java files
$javaFiles = Get-ChildItem -Path $srcRoot -Recurse -Filter *.java | ForEach-Object { $_.FullName }
Write-Host "Compiling $($javaFiles.Count) Java files..."

# Wipe old output
if (Test-Path $outDir) { Remove-Item -Recurse -Force $outDir }
New-Item -ItemType Directory -Path $outDir | Out-Null

$classpath = "$sqliteJar"

& javac `
    --module-path $javafxLib `
    --add-modules javafx.controls,javafx.media,javafx.fxml,javafx.swing `
    -cp $classpath `
    -d $outDir `
    $javaFiles

if ($LASTEXITCODE -ne 0) {
    Write-Host "Compilation failed." -ForegroundColor Red
    exit $LASTEXITCODE
}

# Copy non-Java resources (CSS, SQL, PNG) into out\ so classloader can find them.
# We enumerate everything and filter by extension manually — PowerShell's
# Get-ChildItem -Include has silent-drop quirks that bit us once already.
$resourceExtensions = @('.css', '.sql', '.png')
$srcRootFull = (Resolve-Path $srcRoot).Path

Get-ChildItem -Path $srcRoot -Recurse -File | ForEach-Object {
    if ($resourceExtensions -contains $_.Extension.ToLower()) {
        $relative = $_.FullName.Substring($srcRootFull.Length + 1)
        $dest = Join-Path $outDir $relative
        $destDir = Split-Path $dest -Parent
        if (-not (Test-Path $destDir)) {
            New-Item -ItemType Directory -Path $destDir -Force | Out-Null
        }
        Copy-Item $_.FullName -Destination $dest -Force
        Write-Host "  resource: $relative"
    }
}

Write-Host "Compiled successfully." -ForegroundColor Green