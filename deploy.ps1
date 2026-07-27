# MiaoWang Deploy Script
$adb = "E:/vivo办公套件/pcsuite/adb_41/adb.exe"
$dist = "E:\MapleLeaf\project\MiaoWang\front-project\unpackage\dist\dev\app-android"
$appDir = "/data/data/io.dcloud.uniappx/files/apps/__UNI__19DBD81/www"
$prefsDir = "/data/data/io.dcloud.uniappx/shared_prefs"

Write-Output "=== Pushing resources ==="
& $adb push $dist /data/local/tmp/uni_push
& $adb shell "run-as io.dcloud.uniappx mkdir -p $appDir"
& $adb shell "run-as io.dcloud.uniappx rm -rf $appDir/*"
& $adb shell "run-as io.dcloud.uniappx cp -r /data/local/tmp/uni_push/* $appDir/"
& $adb shell "rm -rf /data/local/tmp/uni_push"

Write-Output "=== Configuring appid ==="
$xml = '<?xml version="1.0" encoding="utf-8" standalone="yes" ?><map><string name="appid">__UNI__19DBD81</string></map>'
$b64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($xml))
& $adb shell "run-as io.dcloud.uniappx sh -c 'echo $b64 | base64 -d > $prefsDir/dcloud_sp.xml'"

Write-Output "=== Restarting ==="
& $adb shell "am force-stop io.dcloud.uniappx"
Start-Sleep -Seconds 1
& $adb shell "am start -n io.dcloud.uniappx/io.dcloud.PandoraEntry"
Write-Output "=== Done! ==="
