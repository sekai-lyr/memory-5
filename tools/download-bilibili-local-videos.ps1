param(
    [string]$OutputDirectory = 'D:\Sekai\_Song\video',
    [string]$AppBaseUrl = 'http://127.0.0.1:8085',
    [string]$UserName = 'sekai',
    [int]$UserId = 3,
    [int]$Quality = 80,
    [int]$MaxAttempts = 12,
    [int]$ShardCount = 1,
    [int]$ShardIndex = 0,
    [long]$MinimumFreeBytes = 15GB,
    [string]$MysqlPath = 'B:\MySQL\bin\mysql.exe'
)

$ErrorActionPreference = 'Stop'

$loginPassword = $env:SEKAI_LOGIN_PASSWORD
$databasePassword = $env:SEKAI_DATABASE_PASSWORD
if ([string]::IsNullOrWhiteSpace($loginPassword)) {
    throw 'SEKAI_LOGIN_PASSWORD is required'
}
if ([string]::IsNullOrWhiteSpace($databasePassword)) {
    throw 'SEKAI_DATABASE_PASSWORD is required'
}
if (-not (Test-Path -LiteralPath $MysqlPath)) {
    throw "MySQL client not found: $MysqlPath"
}
if ($ShardCount -lt 1 -or $ShardIndex -lt 0 -or $ShardIndex -ge $ShardCount) {
    throw "Invalid shard $ShardIndex/$ShardCount"
}

New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null

function Invoke-MysqlQuery([string]$Sql) {
    $previousPassword = $env:MYSQL_PWD
    try {
        $env:MYSQL_PWD = $databasePassword
        $result = & $MysqlPath `
            --host=localhost `
            --port=3306 `
            --user=root `
            --default-character-set=utf8mb4 `
            --batch `
            --skip-column-names `
            --execute=$Sql
        if ($LASTEXITCODE -ne 0) {
            throw "MySQL command failed with exit code $LASTEXITCODE"
        }
        return @($result)
    } finally {
        if ($null -eq $previousPassword) {
            Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
        } else {
            $env:MYSQL_PWD = $previousPassword
        }
    }
}

function Connect-Sekai([System.Net.Http.HttpClient]$Client) {
    $pairs = [System.Collections.Generic.List[System.Collections.Generic.KeyValuePair[string,string]]]::new()
    $pairs.Add([System.Collections.Generic.KeyValuePair[string,string]]::new('userName', $UserName))
    $pairs.Add([System.Collections.Generic.KeyValuePair[string,string]]::new('password', $loginPassword))
    $form = [System.Net.Http.FormUrlEncodedContent]::new($pairs)
    try {
        $response = $Client.PostAsync("$AppBaseUrl/user/login", $form).GetAwaiter().GetResult()
        try {
            if ([int]$response.StatusCode -ne 302) {
                throw "Sekai login failed with HTTP $([int]$response.StatusCode)"
            }
        } finally {
            $response.Dispose()
        }
    } finally {
        $form.Dispose()
    }
}

function Test-Mp4Container([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) {
        return $false
    }
    $bytes = [byte[]]::new(12)
    $stream = [System.IO.File]::OpenRead($Path)
    try {
        $read = $stream.Read($bytes, 0, $bytes.Length)
    } finally {
        $stream.Dispose()
    }
    return $read -eq 12 -and [Text.Encoding]::ASCII.GetString($bytes) -match 'ftyp'
}

function Set-LocalVideoUrl([long]$TrackId, [string]$FileName) {
    $videoUrl = "/uploads/video/$FileName"
    Invoke-MysqlQuery "UPDATE sekai_friend.sekai_song_track SET local_video_url='$videoUrl', update_time=NOW() WHERE id=$TrackId AND user_id=$UserId" | Out-Null
}

function Get-FreeBytes {
    $root = [System.IO.Path]::GetPathRoot((Resolve-Path -LiteralPath $OutputDirectory).Path)
    $driveName = $root.TrimEnd('\').TrimEnd(':')
    return (Get-PSDrive -Name $driveName).Free
}

function Download-Track(
    [System.Net.Http.HttpClient]$Client,
    [long]$TrackId,
    [string]$FileName
) {
    $finalPath = Join-Path $OutputDirectory $FileName
    $partialPath = "$finalPath.part"

    if ((Test-Path -LiteralPath $finalPath) -and (Test-Mp4Container $finalPath)) {
        Set-LocalVideoUrl $TrackId $FileName
        return [pscustomobject]@{ Status = 'existing'; Bytes = (Get-Item -LiteralPath $finalPath).Length; Attempts = 0 }
    }

    $total = $null
    $attempt = 0
    while ($attempt -lt $MaxAttempts) {
        $attempt++
        $offset = if (Test-Path -LiteralPath $partialPath) {
            (Get-Item -LiteralPath $partialPath).Length
        } else {
            0
        }
        if ($total -and $offset -eq $total) {
            break
        }
        if ((Get-FreeBytes) -lt $MinimumFreeBytes) {
            throw "D drive free space is below $MinimumFreeBytes bytes"
        }

        $refresh = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
        $sourceRequest = [System.Net.Http.HttpRequestMessage]::new(
            [System.Net.Http.HttpMethod]::Get,
            "$AppBaseUrl/bilibili/direct/$TrackId`?quality=$Quality&refresh=$refresh"
        )
        try {
            $sourceResponse = $Client.SendAsync(
                $sourceRequest,
                [System.Net.Http.HttpCompletionOption]::ResponseHeadersRead
            ).GetAwaiter().GetResult()
            try {
                if ([int]$sourceResponse.StatusCode -eq 401) {
                    Connect-Sekai $Client
                    continue
                }
                if ([int]$sourceResponse.StatusCode -ne 302 -or -not $sourceResponse.Headers.Location) {
                    Write-Output "track=$TrackId attempt=$attempt source_http=$([int]$sourceResponse.StatusCode)"
                    Start-Sleep -Milliseconds 800
                    continue
                }
                $mediaUri = $sourceResponse.Headers.Location
                if (-not $mediaUri.IsAbsoluteUri) {
                    $mediaUri = [Uri]::new([Uri]$AppBaseUrl, $mediaUri)
                }
            } finally {
                $sourceResponse.Dispose()
            }
        } catch {
            Write-Output "track=$TrackId attempt=$attempt source_error=true"
            Start-Sleep -Milliseconds 800
            continue
        } finally {
            $sourceRequest.Dispose()
        }

        $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Get, $mediaUri)
        $request.Headers.Referrer = [Uri]'https://www.bilibili.com/'
        $request.Headers.TryAddWithoutValidation('User-Agent', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/131.0 Safari/537.36') | Out-Null
        $request.Headers.TryAddWithoutValidation('Accept', '*/*') | Out-Null
        if ($offset -gt 0) {
            $request.Headers.Range = [System.Net.Http.Headers.RangeHeaderValue]::new([int64]$offset, $null)
        }
        try {
            $response = $Client.SendAsync(
                $request,
                [System.Net.Http.HttpCompletionOption]::ResponseHeadersRead
            ).GetAwaiter().GetResult()
            try {
                if ([int]$response.StatusCode -notin 200, 206) {
                    Write-Output "track=$TrackId attempt=$attempt cdn_http=$([int]$response.StatusCode) offset=$offset"
                    Start-Sleep -Milliseconds 800
                    continue
                }
                if ($offset -gt 0 -and [int]$response.StatusCode -ne 206) {
                    [System.IO.File]::Open($partialPath, 'Create').Dispose()
                    $total = $null
                    Write-Output "track=$TrackId attempt=$attempt range_ignored restart=true"
                    continue
                }

                $responseTotal = $null
                if ($response.Content.Headers.ContentRange -and $response.Content.Headers.ContentRange.Length) {
                    $responseTotal = [int64]$response.Content.Headers.ContentRange.Length
                } elseif ($response.Content.Headers.ContentLength) {
                    $responseTotal = $offset + [int64]$response.Content.Headers.ContentLength
                }
                if ($total -and $responseTotal -and $total -ne $responseTotal -and $offset -gt 0) {
                    [System.IO.File]::Open($partialPath, 'Create').Dispose()
                    $total = $null
                    Write-Output "track=$TrackId attempt=$attempt source_changed restart=true"
                    continue
                }
                if ($responseTotal) {
                    $total = $responseTotal
                }

                $input = $response.Content.ReadAsStream()
                $output = [System.IO.FileStream]::new(
                    $partialPath,
                    [System.IO.FileMode]::Append,
                    [System.IO.FileAccess]::Write,
                    [System.IO.FileShare]::None,
                    1048576,
                    [System.IO.FileOptions]::SequentialScan
                )
                try {
                    $input.CopyTo($output, 1048576)
                } catch {
                    Write-Output "track=$TrackId attempt=$attempt interrupted=true"
                } finally {
                    $output.Dispose()
                    $input.Dispose()
                }
            } finally {
                $response.Dispose()
            }
        } catch {
            Write-Output "track=$TrackId attempt=$attempt cdn_error=true offset=$offset"
            Start-Sleep -Milliseconds 800
            continue
        } finally {
            $request.Dispose()
        }

        $written = (Get-Item -LiteralPath $partialPath).Length
        Write-Output "track=$TrackId attempt=$attempt bytes=$written total=$total"
        if ($total -and $written -eq $total) {
            break
        }
        if ($total -and $written -gt $total) {
            throw "Track $TrackId exceeded expected length: $written > $total"
        }
    }

    $actual = if (Test-Path -LiteralPath $partialPath) { (Get-Item -LiteralPath $partialPath).Length } else { 0 }
    if (-not $total -or $actual -ne $total) {
        throw "Track $TrackId is incomplete after $attempt attempts: $actual / $total"
    }
    if (-not (Test-Mp4Container $partialPath)) {
        throw "Track $TrackId did not produce an MP4 container"
    }

    Move-Item -LiteralPath $partialPath -Destination $finalPath -Force
    Set-LocalVideoUrl $TrackId $FileName
    return [pscustomobject]@{ Status = 'downloaded'; Bytes = $actual; Attempts = $attempt }
}

$rows = Invoke-MysqlQuery "SELECT id,audio_url FROM sekai_friend.sekai_song_track WHERE user_id=$UserId AND audio_url LIKE '%bilibili.com%' AND (local_video_url IS NULL OR local_video_url='') AND MOD(id,$ShardCount)=$ShardIndex ORDER BY id"
$tracks = foreach ($row in $rows) {
    $parts = $row -split "`t", 2
    if ($parts.Count -ne 2) {
        continue
    }
    $trackId = [long]$parts[0]
    $cidMatch = [regex]::Match($parts[1], '(?:[?&]cid=)(\d+)')
    $suffix = if ($cidMatch.Success) { "-$($cidMatch.Groups[1].Value)" } else { '' }
    [pscustomobject]@{
        Id = $trackId
        FileName = "bilibili-$trackId$suffix.mp4"
    }
}

$handler = [System.Net.Http.HttpClientHandler]::new()
$handler.AllowAutoRedirect = $false
$handler.CookieContainer = [System.Net.CookieContainer]::new()
$client = [System.Net.Http.HttpClient]::new($handler)
$client.Timeout = [TimeSpan]::FromMinutes(10)

$downloaded = 0
$existing = 0
$failed = 0
$startedAt = Get-Date
try {
    Connect-Sekai $client
    for ($index = 0; $index -lt $tracks.Count; $index++) {
        $track = $tracks[$index]
        try {
            $result = Download-Track $client $track.Id $track.FileName
            if ($result.Status -eq 'existing') {
                $existing++
            } else {
                $downloaded++
            }
            Write-Output "progress=$($index + 1)/$($tracks.Count) track=$($track.Id) status=$($result.Status) bytes=$($result.Bytes) attempts=$($result.Attempts)"
        } catch {
            $failed++
            Write-Output "progress=$($index + 1)/$($tracks.Count) track=$($track.Id) status=failed error=$($_.Exception.Message)"
        }
        Start-Sleep -Milliseconds 250
    }
} finally {
    $client.Dispose()
    $handler.Dispose()
}

[pscustomobject]@{
    total = $tracks.Count
    downloaded = $downloaded
    existing = $existing
    failed = $failed
    elapsed_minutes = [math]::Round(((Get-Date) - $startedAt).TotalMinutes, 2)
    output_directory = $OutputDirectory
} | ConvertTo-Json -Compress

if ($failed -gt 0) {
    exit 2
}
