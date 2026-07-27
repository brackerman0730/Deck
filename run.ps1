# ============================================================================
# Deck - run script
# ----------------------------------------------------------------------------
# Launches the compiled application.
# ============================================================================

$ErrorActionPreference = "Stop"

$javafxLib = "C:\javafx-sdk-26.0.1\lib"
$sqliteJar = "lib\sqlite-jdbc-3.53.2.0.jar"
$outDir    = "out"

if (-not (Test-Path $outDir)) {
    Write-Host "No compiled output found. Run .\compile.ps1 first." -ForegroundColor Red
    exit 1
}

$classpath = "$outDir;$sqliteJar"

& java `
    --module-path $javafxLib `
    --add-modules javafx.controls,javafx.media,javafx.fxml,javafx.swing `
    "-Djavax.net.ssl.trustStoreType=Windows-ROOT" `
    --enable-native-access=javafx.graphics `
    --enable-native-access=ALL-UNNAMED `
    -cp $classpath `
    com.deck.app.Main