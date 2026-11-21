<##
 XAND MUSIC TikTok automation - deterministic cover/MP4 branding stage.
 Uses the exact official logo already saved privately by music.xand.share.
 No editor source changes, no account credentials, no screenshots, no publishing.
##>
param(
  [Parameter(Mandatory=$true)][string]$BaseImage,
  [Parameter(Mandatory=$true)][string]$AdbExe,
  [Parameter(Mandatory=$true)][string]$Serial,
  [string]$FfmpegExe = 'C:\Users\Windows 11\AppData\Local\Programs\Kdenlive\bin\ffmpeg.exe',
  [string]$OutputDir = 'C:\Users\Windows 11\Documents\XAND-MUSIC\artifacts',
  [ValidateRange(60,180)][int]$DurationSeconds = 60,
  [string]$Stem = 'XAND_MyLifeMyRules'
)

$ErrorActionPreference='Stop'
foreach($file in @($BaseImage,$AdbExe,$FfmpegExe)) {
  if(-not (Test-Path -LiteralPath $file -PathType Leaf)) {throw "Arquivo obrigatório não encontrado: $file"}
}
if($Serial -notmatch '^[0-9a-zA-Z.:-]+$') {throw 'Identificador ADB inválido.'}
if($Stem -notmatch '^[A-Za-z0-9_-]{2,50}$') {throw 'Nome de saída inválido.'}
New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
$id=[DateTimeOffset]::Now.ToString('yyyyMMdd_HHmmss')+'_'+[Guid]::NewGuid().ToString('N').Substring(0,8)
$cover=Join-Path $OutputDir ($Stem+'_'+$id+'.png')
$video=Join-Path $OutputDir ($Stem+'_'+$id+'.mp4')
$temporary=Join-Path ([IO.Path]::GetTempPath()) ('xand-brand-'+[Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $temporary | Out-Null
$logo=Join-Path $temporary 'official-logo.png'
try {
  $psi=[Diagnostics.ProcessStartInfo]::new()
  $psi.FileName=$AdbExe
  foreach($a in @('-s',$Serial,'exec-out','run-as','music.xand.share','cat','files/xand_logo_original.png')) { [void]$psi.ArgumentList.Add($a) }
  $psi.UseShellExecute=$false
  $psi.RedirectStandardOutput=$true
  $p=[Diagnostics.Process]::Start($psi)
  $stream=[IO.File]::Create($logo)
  try {$p.StandardOutput.BaseStream.CopyTo($stream)} finally {$stream.Dispose()}
  $p.WaitForExit(30000) | Out-Null
  if($p.ExitCode -ne 0 -or (Get-Item -LiteralPath $logo).Length -lt 1000) {throw 'Não foi possível ler a logo oficial do app.'}
  $header=[IO.File]::ReadAllBytes($logo)
  if(-not ($header[0] -eq 137 -and $header[1] -eq 80 -and $header[2] -eq 78 -and $header[3] -eq 71)) {throw 'Logo oficial não é um PNG válido.'}

  # The existing editor applies a dark band at y=1510..1755 even with blank text.
  # Keep every white pixel of the genuine logo ABOVE that band, still in the lower part.
  # The source art has an empty black branding region starting at y=1090.
  $filter='[0:v]scale=1080:1920,format=rgba[base];[1:v]scale=680:-1,format=rgba[mark];[base][mark]overlay=(W-w)/2:1090:format=auto[out]'
  & $FfmpegExe -hide_banner -loglevel error -y -i $BaseImage -i $logo -filter_complex $filter -map '[out]' -frames:v 1 $cover
  if($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $cover -PathType Leaf)) {throw 'Erro ao compor a capa.'}
  & $FfmpegExe -hide_banner -loglevel error -y -loop 1 -framerate 15 -i $cover -t $DurationSeconds -c:v libx264 -preset veryfast -crf 22 -pix_fmt yuv420p -movflags +faststart -an $video
  if($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $video -PathType Leaf)) {throw 'Erro ao exportar o MP4 de origem.'}
  $length=(Get-Item -LiteralPath $video).Length
  if($length -lt 10000) {throw 'MP4 produzido muito pequeno; verificar antes de usar.'}
  [pscustomobject]@{cover=$cover;video=$video;duration_seconds=$DurationSeconds;size_bytes=$length;logo='exact_original_from_android';editor_changed=$false;published=$false} | ConvertTo-Json -Compress
} finally {
  if(Test-Path -LiteralPath $temporary) {Remove-Item -LiteralPath $temporary -Recurse -Force}
}
