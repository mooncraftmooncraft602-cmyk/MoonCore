# =====================================================================
#  Démarre la MariaDB locale de MoonCore (base 'mooncore' sur 127.0.0.1:3306).
#  À lancer AVANT de démarrer le serveur Minecraft (la base doit tourner).
#  Laisse cette fenêtre ouverte pendant le jeu.
#
#  (Astuce : pour un service Windows permanent, lance une fois en ADMIN :
#   & "C:\Program Files\MariaDB 12.3\bin\mariadb-install-db.exe" `
#       --datadir="C:\Users\PC\mooncore-mariadb\data" --service=MariaDB --port=3306 --password=MoonRoot123
#   puis: Start-Service MariaDB )
# =====================================================================
$bin  = "C:\Program Files\MariaDB 12.3\bin"
$data = "C:\Users\PC\mooncore-mariadb\data"

$inUse = Test-NetConnection -ComputerName 127.0.0.1 -Port 3306 -InformationLevel Quiet -WarningAction SilentlyContinue
if ($inUse) {
    Write-Host "MariaDB semble déjà à l'écoute sur 127.0.0.1:3306." -ForegroundColor Yellow
    return
}
Write-Host "Démarrage de MariaDB (mooncore) sur 127.0.0.1:3306..." -ForegroundColor Green
& "$bin\mariadbd.exe" --datadir="$data" --port=3306 --bind-address=127.0.0.1 --console
