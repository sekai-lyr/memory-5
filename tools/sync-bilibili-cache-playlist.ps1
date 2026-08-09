param(
    [string]$CacheRoot = "E:\Blibli",
    [string]$FfmpegPath = "C:\Program Files\Tencent\QQBrowser\21.0.8365.400\ffmpeg\ffmpeg.exe",
    [string]$Database = "sekai_friend",
    [long]$PlaylistId = 15
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $CacheRoot)) {
    throw "Cache root not found: $CacheRoot"
}
if (-not (Test-Path -LiteralPath $FfmpegPath)) {
    throw "ffmpeg not found: $FfmpegPath"
}

$env:MYSQL_PWD = "123456"
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

$playlistExists = mysql -uroot -N -B -D $Database -e "SELECT COUNT(*) FROM sekai_song_playlist WHERE id=$PlaylistId"
if ($playlistExists -ne "1") {
    throw "Playlist not found: $PlaylistId"
}

$cacheByCid = @{}
foreach ($dir in Get-ChildItem -LiteralPath $CacheRoot -Directory) {
    $files = Get-ChildItem -LiteralPath $dir.FullName -File -Filter "*.m4s" -ErrorAction SilentlyContinue
    $video = $files |
        Where-Object { $_.Name -match "-1-(30080|100050|100051|100052|100053|100054|100060|100061|100062|100063|100064|30077|30078|30079)\.m4s$" } |
        Sort-Object Length -Descending |
        Select-Object -First 1
    $audio = $files |
        Where-Object { $_.Name -match "-1-30280\.m4s$" } |
        Sort-Object Length -Descending |
        Select-Object -First 1
    if ($video -and $audio) {
        $cacheByCid[$dir.Name] = [pscustomobject]@{
            Cid = $dir.Name
            Video = $video.FullName
            Audio = $audio.FullName
        }
    }
}

$rows = mysql -uroot -N -B -D $Database -e "SELECT t.id,t.audio_url,COALESCE(t.local_video_url,'') FROM sekai_song_playlist_song ps JOIN sekai_song_track t ON t.id=ps.song_id WHERE ps.playlist_id=$PlaylistId ORDER BY t.id"
$alreadyBound = New-Object System.Collections.Generic.List[object]
$toImport = New-Object System.Collections.Generic.List[object]
$toRemove = New-Object System.Collections.Generic.List[object]

foreach ($line in $rows) {
    $parts = $line -split "`t"
    if ($parts.Count -lt 3) {
        continue
    }
    $id = [long]$parts[0]
    $audioUrl = $parts[1]
    $localVideoUrl = $parts[2]
    $cid = ""
    if ($audioUrl -match "cid=([0-9]+)") {
        $cid = $Matches[1]
    }
    $hasCache = $cid -and $cacheByCid.ContainsKey($cid)
    if ($localVideoUrl -like "/uploads/video/*") {
        $alreadyBound.Add([pscustomobject]@{Id=$id; Cid=$cid}) | Out-Null
    } elseif ($hasCache) {
        $cache = $cacheByCid[$cid]
        $outputName = "bilibili-$id-$cid.mp4"
        $toImport.Add([pscustomobject]@{
            Id = $id
            Cid = $cid
            Video = $cache.Video
            Audio = $cache.Audio
            OutputName = $outputName
            OutputPath = Join-Path $uploadRoot $outputName
        }) | Out-Null
    } else {
        $toRemove.Add([pscustomobject]@{Id=$id; Cid=$cid}) | Out-Null
    }
}

Write-Host "PLAYLIST_ID=$PlaylistId"
Write-Host "PLAYLIST_ROWS=$($rows.Count)"
Write-Host "VALID_CACHE_PAIRS=$($cacheByCid.Count)"
Write-Host "ALREADY_BOUND_KEEP=$($alreadyBound.Count)"
Write-Host "NEED_IMPORT=$($toImport.Count)"
Write-Host "REMOVE_FROM_PLAYLIST=$($toRemove.Count)"

$imported = New-Object System.Collections.Generic.List[object]
$failures = New-Object System.Collections.Generic.List[object]
$index = 0
foreach ($item in $toImport) {
    $index++
    Write-Host ("[{0}/{1}] merge id={2} cid={3}" -f $index, $toImport.Count, $item.Id, $item.Cid)
    $cleanVideo = Join-Path $tempRoot "$($item.Cid)-video.m4s"
    $cleanAudio = Join-Path $tempRoot "$($item.Cid)-audio.m4s"
    try {
        Copy-CleanM4s $item.Video $cleanVideo
        Copy-CleanM4s $item.Audio $cleanAudio
        & $FfmpegPath -hide_banner -loglevel error -y -i $cleanVideo -i $cleanAudio -c copy -movflags +faststart $item.OutputPath
        if ($LASTEXITCODE -eq 0 -and (Test-Path -LiteralPath $item.OutputPath) -and ((Get-Item -LiteralPath $item.OutputPath).Length -gt 0)) {
            $imported.Add($item) | Out-Null
        } else {
            $failures.Add($item) | Out-Null
        }
    } finally {
        Remove-Item -LiteralPath $cleanVideo, $cleanAudio -Force -ErrorAction SilentlyContinue
    }
}

if ($failures.Count -gt 0 -or $imported.Count -ne $toImport.Count) {
    Write-Host "IMPORT_SUCCESS=$($imported.Count)"
    Write-Host "IMPORT_FAILURE=$($failures.Count)"
    $failures | Format-Table Id, Cid, Video, Audio, OutputPath -AutoSize
    throw "Import did not finish cleanly. Database and playlist were not updated."
}

$sql = "START TRANSACTION;`n"
foreach ($item in $imported) {
    $url = "/uploads/video/$($item.OutputName)"
    $sql += "UPDATE sekai_song_track SET local_video_url='$url', update_time=NOW() WHERE id=$($item.Id) AND audio_url LIKE '%cid=$($item.Cid)%';`n"
}
if ($toRemove.Count -gt 0) {
    $removeIds = (($toRemove | ForEach-Object { $_.Id }) -join ",")
    $sql += "DELETE FROM sekai_song_playlist_song WHERE playlist_id=$PlaylistId AND song_id IN ($removeIds);`n"
}
$sql += "COMMIT;`n"
$sql | mysql -uroot -D $Database

$after = mysql -uroot -N -B -D $Database -e "SELECT COUNT(*),SUM(t.local_video_url LIKE '/uploads/video/%'),SUM(t.local_video_url IS NULL OR t.local_video_url NOT LIKE '/uploads/video/%') FROM sekai_song_playlist_song ps JOIN sekai_song_track t ON t.id=ps.song_id WHERE ps.playlist_id=$PlaylistId"
Write-Host "IMPORT_SUCCESS=$($imported.Count)"
Write-Host "REMOVED_FROM_PLAYLIST=$($toRemove.Count)"
Write-Host "AFTER_COUNTS=$after"
