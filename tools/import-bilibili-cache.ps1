param(
    [string]$CacheRoot = "E:\Blibli",
    [string]$FfmpegPath = "C:\Program Files\Tencent\QQBrowser\21.0.8365.400\ffmpeg\ffmpeg.exe",
    [string]$Database = "sekai_friend"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $CacheRoot)) {
    throw "Cache root not found: $CacheRoot"
}
if (-not (Test-Path -LiteralPath $FfmpegPath)) {
    throw "ffmpeg not found: $FfmpegPath"
}

$uploadRoot = Join-Path (Get-Location) "uploads\video"
$tempRoot = Join-Path (Get-Location) "target\bilibili-cache-clean"
New-Item -ItemType Directory -Path $uploadRoot -Force | Out-Null
New-Item -ItemType Directory -Path $tempRoot -Force | Out-Null

function Copy-CleanM4s($source, $destination) {
    $inputStream = [System.IO.File]::OpenRead($source)
    try {
        $prefix = New-Object byte[] 9
        $read = $inputStream.Read($prefix, 0, 9)
        $hasBilibiliPrefix = $read -eq 9
        if ($hasBilibiliPrefix) {
            foreach ($value in $prefix) {
                if ($value -ne 0x30) {
                    $hasBilibiliPrefix = $false
                    break
                }
            }
        }
        if ($hasBilibiliPrefix) {
            $inputStream.Seek(9, [System.IO.SeekOrigin]::Begin) | Out-Null
        } else {
            $inputStream.Seek(0, [System.IO.SeekOrigin]::Begin) | Out-Null
        }
        $outputStream = [System.IO.File]::Create($destination)
        try {
            $inputStream.CopyTo($outputStream)
        } finally {
            $outputStream.Dispose()
        }
    } finally {
        $inputStream.Dispose()
    }
}

$env:MYSQL_PWD = "123456"
$dbLines = mysql -uroot -N -B -D $Database -e "SELECT id,SUBSTRING_INDEX(audio_url,'cid=',-1) FROM sekai_song_track WHERE audio_url LIKE '%bilibili.com%' AND audio_url LIKE '%cid=%'"
$dbByCid = @{}
foreach ($line in $dbLines) {
    $parts = $line -split "`t"
    if ($parts.Count -lt 2) {
        continue
    }
    $cid = (($parts[1] -split "&")[0]).Trim()
    if ($cid) {
        $dbByCid[$cid] = [long]$parts[0]
    }
}

$cacheDirs = Get-ChildItem -LiteralPath $CacheRoot -Directory
$mappings = foreach ($dir in $cacheDirs) {
    $cid = $dir.Name
    if (-not $dbByCid.ContainsKey($cid)) {
        continue
    }
    $files = Get-ChildItem -LiteralPath $dir.FullName -File -Filter "*.m4s"
    $video = $files |
        Where-Object { $_.Name -match "-1-(30080|100050|100051|100052|100053|100054|100060|100061|100062|100063|100064|30077|30078|30079)\.m4s$" } |
        Sort-Object Length -Descending |
        Select-Object -First 1
    $audio = $files |
        Where-Object { $_.Name -match "-1-30280\.m4s$" } |
        Sort-Object Length -Descending |
        Select-Object -First 1
    if ($video -and $audio) {
        $outputName = "bilibili-$($dbByCid[$cid])-$cid.mp4"
        [pscustomobject]@{
            Id = $dbByCid[$cid]
            Cid = $cid
            Video = $video.FullName
            Audio = $audio.FullName
            OutputName = $outputName
            OutputPath = Join-Path $uploadRoot $outputName
        }
    }
}

if ($cacheDirs.Count -ne 76 -or $mappings.Count -ne 76) {
    throw "Count mismatch. Cache directories: $($cacheDirs.Count). Matched mappings: $($mappings.Count)."
}

$success = New-Object System.Collections.Generic.List[object]
$failures = New-Object System.Collections.Generic.List[object]
$index = 0
foreach ($item in ($mappings | Sort-Object Id)) {
    $index++
    Write-Host ("[{0}/76] merge id={1} cid={2}" -f $index, $item.Id, $item.Cid)
    if (Test-Path -LiteralPath $item.OutputPath) {
        Remove-Item -LiteralPath $item.OutputPath -Force
    }
    $cleanVideo = Join-Path $tempRoot "$($item.Cid)-video.m4s"
    $cleanAudio = Join-Path $tempRoot "$($item.Cid)-audio.m4s"
    try {
        Copy-CleanM4s $item.Video $cleanVideo
        Copy-CleanM4s $item.Audio $cleanAudio
        & $FfmpegPath -hide_banner -loglevel error -y -i $cleanVideo -i $cleanAudio -c copy -movflags +faststart $item.OutputPath
        if ($LASTEXITCODE -eq 0 -and (Test-Path -LiteralPath $item.OutputPath) -and ((Get-Item -LiteralPath $item.OutputPath).Length -gt 0)) {
            $success.Add($item) | Out-Null
        } else {
            $failures.Add($item) | Out-Null
        }
    } finally {
        Remove-Item -LiteralPath $cleanVideo, $cleanAudio -Force -ErrorAction SilentlyContinue
    }
}

if ($failures.Count -gt 0 -or $success.Count -ne 76) {
    Write-Host "MERGE_SUCCESS=$($success.Count)"
    Write-Host "MERGE_FAILURE=$($failures.Count)"
    $failures | Format-Table Id, Cid, Video, Audio, OutputPath -AutoSize
    throw "Merge did not produce exactly 76 videos. Database was not updated."
}

$sql = "START TRANSACTION;`n"
foreach ($item in $success) {
    $url = "/uploads/video/$($item.OutputName)"
    $sql += "UPDATE sekai_song_track SET local_video_url='$url', update_time=NOW() WHERE id=$($item.Id) AND audio_url LIKE '%cid=$($item.Cid)%';`n"
}
$sql += "COMMIT;`n"
$sql | mysql -uroot -D $Database

$idList = (($success | Sort-Object Id | ForEach-Object { $_.Id }) -join ",")
$updated = mysql -uroot -N -B -D $Database -e "SELECT COUNT(*) FROM sekai_song_track WHERE id IN ($idList) AND local_video_url LIKE '/uploads/video/bilibili-%'"
$generated = @(Get-ChildItem -LiteralPath $uploadRoot -File -Filter "bilibili-*.mp4").Count

Write-Host "MERGE_SUCCESS=$($success.Count)"
Write-Host "DB_UPDATED=$updated"
Write-Host "GENERATED_FILES=$generated"
