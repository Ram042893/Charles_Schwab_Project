$ErrorActionPreference = 'Continue'
$base = 'http://localhost:8080'
$pass = 0
$fail = 0
$results = New-Object System.Collections.Generic.List[string]
$flagsOn = $false

function Invoke-Curl {
    param(
        [string]$Method,
        [string]$Url,
        [hashtable]$Headers = @{},
        [string]$JsonBody
    )
    $hdrFile = [System.IO.Path]::GetTempFileName()
    $bodyFile = [System.IO.Path]::GetTempFileName()
    $argList = New-Object System.Collections.Generic.List[string]
    $argList.Add('-sS') | Out-Null
    $argList.Add('-X') | Out-Null
    $argList.Add($Method) | Out-Null
    $argList.Add($Url) | Out-Null
    $argList.Add('-D') | Out-Null
    $argList.Add($hdrFile) | Out-Null
    $argList.Add('-o') | Out-Null
    $argList.Add($bodyFile) | Out-Null
    $argList.Add('-w') | Out-Null
    $argList.Add('%{http_code}') | Out-Null
    foreach ($k in $Headers.Keys) {
        $argList.Add('-H') | Out-Null
        $argList.Add("${k}: $($Headers[$k])") | Out-Null
    }
    if ($null -ne $JsonBody) {
        $jsonFile = [System.IO.Path]::GetTempFileName()
        [System.IO.File]::WriteAllText($jsonFile, $JsonBody, [System.Text.UTF8Encoding]::new($false))
        $argList.Add('-H') | Out-Null
        $argList.Add('Content-Type: application/json') | Out-Null
        $argList.Add('--data-binary') | Out-Null
        $argList.Add("@$jsonFile") | Out-Null
    }
    $code = (& curl.exe @($argList.ToArray())).Trim()
    $body = ''
    if (Test-Path $bodyFile) {
        $body = [System.IO.File]::ReadAllText($bodyFile)
    }
    $rawHeaders = [System.IO.File]::ReadAllText($hdrFile)
    $headerMap = @{}
    foreach ($line in ($rawHeaders -split "`r?`n")) {
        if ($line -match '^([^:]+):\s*(.*)$') {
            $headerMap[$matches[1].ToLowerInvariant()] = $matches[2].Trim()
        }
    }
    $json = $null
    $trimmed = if ($body) { $body.Trim() } else { '' }
    if ($trimmed.StartsWith('{') -or $trimmed.StartsWith('[')) {
        try { $json = $body | ConvertFrom-Json } catch { $json = $null }
    }
    return [pscustomobject]@{
        Status  = [int]$code
        Body    = $body
        Json    = $json
        Headers = $headerMap
    }
}

function Assert-Eq {
    param($Name, $Actual, $Expected)
    if ("$Actual" -eq "$Expected") {
        $script:pass++
        $script:results.Add("PASS  $Name => $Actual") | Out-Null
    } else {
        $script:fail++
        $script:results.Add("FAIL  $Name expected=[$Expected] actual=[$Actual]") | Out-Null
    }
}

function Assert-True {
    param($Name, $Condition, $Detail)
    if ($Condition) {
        $script:pass++
        $script:results.Add("PASS  $Name") | Out-Null
    } else {
        $script:fail++
        $script:results.Add("FAIL  $Name :: $Detail") | Out-Null
    }
}

function AuthHeaders($token) {
    return @{ Authorization = "Bearer $token" }
}

$health = Invoke-Curl GET "$base/actuator/health"
Assert-Eq 'GET /actuator/health status' $health.Status 200
Assert-Eq 'health.status' $health.Json.status 'UP'

$login = Invoke-Curl POST "$base/api/v1/auth/login" -JsonBody '{"username":"engineer","password":"Engineer@123"}'
Assert-Eq 'POST /api/v1/auth/login status' $login.Status 200
Assert-True 'login.accessToken present' (-not [string]::IsNullOrWhiteSpace($login.Json.accessToken)) $login.Body
Assert-True 'login.refreshToken present' (-not [string]::IsNullOrWhiteSpace($login.Json.refreshToken)) $login.Body
Assert-Eq 'login.tokenType' $login.Json.tokenType 'Bearer'
Assert-Eq 'login.username' $login.Json.username 'engineer'
Assert-True 'login.roles contains ENGINEER' ($login.Json.roles -match 'ENGINEER') $login.Json.roles
$engToken = $login.Json.accessToken
$refreshToken = $login.Json.refreshToken

$badLogin = Invoke-Curl POST "$base/api/v1/auth/login" -JsonBody '{"username":"engineer","password":"wrong"}'
Assert-Eq 'bad password status' $badLogin.Status 401
Assert-Eq 'bad password message' $badLogin.Json.message 'Invalid credentials'

$missingUser = Invoke-Curl POST "$base/api/v1/auth/login" -JsonBody '{"username":"","password":"x"}'
Assert-Eq 'empty username status' $missingUser.Status 400
Assert-Eq 'empty username message' $missingUser.Json.message 'Validation failed'

$refreshed = Invoke-Curl POST "$base/api/v1/auth/refresh" -JsonBody ('{"refreshToken":"' + $refreshToken + '"}')
Assert-Eq 'POST /api/v1/auth/refresh status' $refreshed.Status 200
Assert-Eq 'refresh.username' $refreshed.Json.username 'engineer'
Assert-True 'refresh issued new accessToken' (-not [string]::IsNullOrWhiteSpace($refreshed.Json.accessToken)) $refreshed.Body
$engToken = $refreshed.Json.accessToken

$badRefresh = Invoke-Curl POST "$base/api/v1/auth/refresh" -JsonBody '{"refreshToken":"not-a-jwt"}'
Assert-Eq 'bad refresh status' $badRefresh.Status 401
Assert-Eq 'bad refresh message' $badRefresh.Json.message 'Invalid refresh token'

$unauth = Invoke-Curl POST "$base/api/v1/urls" -JsonBody '{"targetUrl":"https://example.com/noauth"}'
Assert-True 'unauthenticated POST /urls blocked (401 or 403)' ($unauth.Status -eq 401 -or $unauth.Status -eq 403) "status=$($unauth.Status) body=$($unauth.Body)"

$blank = Invoke-Curl POST "$base/api/v1/urls" -Headers (AuthHeaders $engToken) -JsonBody '{"targetUrl":""}'
Assert-Eq 'blank targetUrl status' $blank.Status 400
Assert-Eq 'blank targetUrl message' $blank.Json.message 'Validation failed'

$js = Invoke-Curl POST "$base/api/v1/urls" -Headers (AuthHeaders $engToken) -JsonBody '{"targetUrl":"javascript:alert(1)"}'
Assert-Eq 'javascript URL status' $js.Status 400
Assert-Eq 'javascript URL message' $js.Json.message 'Target URL must include scheme and host'

$ftp = Invoke-Curl POST "$base/api/v1/urls" -Headers (AuthHeaders $engToken) -JsonBody '{"targetUrl":"ftp://example.com/file"}'
Assert-Eq 'ftp URL status' $ftp.Status 400
Assert-Eq 'ftp URL message' $ftp.Json.message 'Only http/https URLs can be shortened'

$loopback = Invoke-Curl POST "$base/api/v1/urls" -Headers (AuthHeaders $engToken) -JsonBody '{"targetUrl":"http://127.0.0.1/admin"}'
Assert-Eq 'loopback URL status' $loopback.Status 400
Assert-Eq 'loopback URL message' $loopback.Json.message 'Private or non-routable destinations are not allowed'

$localhost = Invoke-Curl POST "$base/api/v1/urls" -Headers (AuthHeaders $engToken) -JsonBody '{"targetUrl":"http://localhost/secret"}'
Assert-Eq 'localhost URL status' $localhost.Status 400
Assert-Eq 'localhost URL message' $localhost.Json.message 'Private or metadata hosts are not allowed'

$noHost = Invoke-Curl POST "$base/api/v1/urls" -Headers (AuthHeaders $engToken) -JsonBody '{"targetUrl":"not-a-url"}'
Assert-Eq 'invalid URI status' $noHost.Status 400
Assert-Eq 'invalid URI message' $noHost.Json.message 'Target URL must include scheme and host'

$stamp = Get-Date -Format 'yyyyMMddHHmmss'
$target = "https://example.com/live-api-$stamp"
$created = Invoke-Curl POST "$base/api/v1/urls" -Headers (AuthHeaders $engToken) -JsonBody ('{"targetUrl":"' + $target + '"}')
Assert-Eq 'POST /api/v1/urls status' $created.Status 201
Assert-True 'created.code present' (-not [string]::IsNullOrWhiteSpace($created.Json.code)) $created.Body
Assert-Eq 'created.targetUrl' $created.Json.targetUrl $target
Assert-Eq 'created.ownerUsername' $created.Json.ownerUsername 'engineer'
Assert-Eq 'created.active' ([string]$created.Json.active) 'True'
Assert-Eq 'created.clickCount' $created.Json.clickCount 0
Assert-True 'created.createdAt present' (-not [string]::IsNullOrWhiteSpace($created.Json.createdAt)) $created.Body
$code = $created.Json.code

$got = Invoke-Curl GET "$base/api/v1/urls/$code" -Headers (AuthHeaders $engToken)
Assert-Eq "GET /api/v1/urls/$code status" $got.Status 200
Assert-Eq 'get.code' $got.Json.code $code
Assert-Eq 'get.targetUrl' $got.Json.targetUrl $target
Assert-Eq 'get.active' ([string]$got.Json.active) 'True'

$listed = Invoke-Curl GET "$base/api/v1/urls" -Headers (AuthHeaders $engToken)
Assert-Eq 'GET /api/v1/urls status' $listed.Status 200
$listedCodes = @($listed.Json | ForEach-Object { $_.code })
Assert-True 'list contains created code' ($listedCodes -contains $code) ("codes=" + ($listedCodes -join ','))

$missing = Invoke-Curl GET "$base/api/v1/urls/does-not-exist-xyz" -Headers (AuthHeaders $engToken)
Assert-Eq 'unknown code status' $missing.Status 404
Assert-Eq 'unknown code message' $missing.Json.message 'Short URL not found'

$redir1 = Invoke-Curl GET "$base/s/$code" -Headers @{ Referer = 'https://referrer.example/page' }
Assert-Eq "GET /s/$code first redirect status" $redir1.Status 302
Assert-Eq 'redirect Location' $redir1.Headers['location'] $target

$analytics1 = Invoke-Curl GET "$base/api/v1/urls/$code/analytics" -Headers (AuthHeaders $engToken)
Assert-Eq 'analytics after 1 click status' $analytics1.Status 200
Assert-Eq 'analytics.code' $analytics1.Json.code $code
Assert-Eq 'analytics.targetUrl' $analytics1.Json.targetUrl $target
Assert-Eq 'analytics.clickCount after 1' $analytics1.Json.clickCount 1
Assert-Eq 'analytics.storedEvents after 1' $analytics1.Json.storedEvents 1
Assert-True 'analytics.recentClicks has 1' (@($analytics1.Json.recentClicks).Count -eq 1) "count=$(@($analytics1.Json.recentClicks).Count)"
Assert-Eq 'analytics.referrer' $analytics1.Json.recentClicks[0].referrer 'https://referrer.example/page'
Assert-True 'analytics.clickedAt present' (-not [string]::IsNullOrWhiteSpace($analytics1.Json.recentClicks[0].clickedAt)) $analytics1.Body

$redir2 = Invoke-Curl GET "$base/s/$code"
Assert-Eq "GET /s/$code second redirect status" $redir2.Status 302

$analytics2 = Invoke-Curl GET "$base/api/v1/urls/$code/analytics" -Headers (AuthHeaders $engToken)
Assert-Eq 'analytics.clickCount after 2' $analytics2.Json.clickCount 2
Assert-Eq 'analytics.storedEvents after 2' $analytics2.Json.storedEvents 2

$unknownRedirect = Invoke-Curl GET "$base/s/no-such-code"
Assert-Eq 'unknown redirect status' $unknownRedirect.Status 404
Assert-Eq 'unknown redirect message' $unknownRedirect.Json.message 'Short URL not found'

$revLogin = Invoke-Curl POST "$base/api/v1/auth/login" -JsonBody '{"username":"reviewer","password":"Reviewer@123"}'
Assert-Eq 'reviewer login status' $revLogin.Status 200
$revToken = $revLogin.Json.accessToken
Assert-True 'reviewer roles' ($revLogin.Json.roles -match 'REVIEWER') $revLogin.Json.roles

$stolen = Invoke-Curl GET "$base/api/v1/urls/$code" -Headers (AuthHeaders $revToken)
Assert-Eq 'cross-user GET status' $stolen.Status 403
Assert-Eq 'cross-user GET message' $stolen.Json.message 'You do not own this short URL'

$stolenAnalytics = Invoke-Curl GET "$base/api/v1/urls/$code/analytics" -Headers (AuthHeaders $revToken)
Assert-Eq 'cross-user analytics status' $stolenAnalytics.Status 403
Assert-Eq 'cross-user analytics message' $stolenAnalytics.Json.message 'You do not own this short URL'

$stolenDelete = Invoke-Curl DELETE "$base/api/v1/urls/$code" -Headers (AuthHeaders $revToken)
Assert-Eq 'cross-user DELETE status' $stolenDelete.Status 403
Assert-Eq 'cross-user DELETE message' $stolenDelete.Json.message 'You do not own this short URL'

$aliasName = "live$stamp"
$aliasTry = Invoke-Curl POST "$base/api/v1/urls" -Headers (AuthHeaders $engToken) -JsonBody ('{"targetUrl":"https://example.com/alias-' + $stamp + '","customAlias":"' + $aliasName + '"}')
if ($aliasTry.Status -eq 201) {
    $flagsOn = $true
    Assert-Eq 'custom alias status (flags ON)' $aliasTry.Status 201
    Assert-Eq 'custom alias code' $aliasTry.Json.code $aliasName
    Assert-Eq 'custom alias target' $aliasTry.Json.targetUrl "https://example.com/alias-$stamp"

    $aliasRedir = Invoke-Curl GET "$base/s/$aliasName"
    Assert-Eq 'custom alias redirect status' $aliasRedir.Status 302
    Assert-Eq 'custom alias Location' $aliasRedir.Headers['location'] "https://example.com/alias-$stamp"

    $dup = Invoke-Curl POST "$base/api/v1/urls" -Headers (AuthHeaders $engToken) -JsonBody ('{"targetUrl":"https://example.com/dup","customAlias":"' + $aliasName + '"}')
    Assert-Eq 'duplicate alias status' $dup.Status 409
    Assert-Eq 'duplicate alias message' $dup.Json.message 'Custom alias is already in use'

    $badAlias = Invoke-Curl POST "$base/api/v1/urls" -Headers (AuthHeaders $engToken) -JsonBody '{"targetUrl":"https://example.com/x","customAlias":"ab"}'
    Assert-Eq 'invalid alias status' $badAlias.Status 400
    Assert-Eq 'invalid alias message' $badAlias.Json.message 'Custom alias must be 4-40 letters, digits, underscores or hyphens'

    $exp = Invoke-Curl POST "$base/api/v1/urls" -Headers (AuthHeaders $engToken) -JsonBody '{"targetUrl":"https://example.com/expires","expiresAt":"2027-12-31T23:59:59Z"}'
    Assert-Eq 'future expiration create status' $exp.Status 201
    Assert-True 'expiresAt stored' ($exp.Json.expiresAt -match '2027-12-31') $exp.Body

    $expiredCodeName = "expd$stamp"
    $expired = Invoke-Curl POST "$base/api/v1/urls" -Headers (AuthHeaders $engToken) -JsonBody ('{"targetUrl":"https://example.com/already-expired","customAlias":"' + $expiredCodeName + '","expiresAt":"2020-01-01T00:00:00Z"}')
    Assert-Eq 'past expiration create status' $expired.Status 201
    $expiredRedir = Invoke-Curl GET "$base/s/$expiredCodeName"
    Assert-Eq 'expired redirect status' $expiredRedir.Status 410
    Assert-Eq 'expired redirect message' $expiredRedir.Json.message 'Short URL has expired'

    $csv = Invoke-Curl GET "$base/api/v1/urls/$code/analytics/export" -Headers (AuthHeaders $engToken)
    Assert-Eq 'CSV export status' $csv.Status 200
    Assert-True 'CSV content-type' ($csv.Headers['content-type'] -match 'text/csv') $csv.Headers['content-type']
    Assert-True 'CSV header row' ($csv.Body -match 'code,targetUrl,clickCount,sampledClicks') $csv.Body
    Assert-True 'CSV includes code' ($csv.Body -match [regex]::Escape($code)) $csv.Body
    Assert-True 'CSV includes clickCount 2' ($csv.Body -match "$code,$target,2,") $csv.Body
} elseif ($aliasTry.Status -eq 409 -and $aliasTry.Json.message -eq 'Custom aliases are not enabled') {
    Assert-Eq 'custom alias status (flags OFF)' $aliasTry.Status 409
    Assert-Eq 'custom alias message (flags OFF)' $aliasTry.Json.message 'Custom aliases are not enabled'

    $expOff = Invoke-Curl POST "$base/api/v1/urls" -Headers (AuthHeaders $engToken) -JsonBody '{"targetUrl":"https://example.com/expires","expiresAt":"2027-12-31T23:59:59Z"}'
    Assert-Eq 'expiration disabled status' $expOff.Status 409
    Assert-Eq 'expiration disabled message' $expOff.Json.message 'Link expiration is not enabled'

    $csvOff = Invoke-Curl GET "$base/api/v1/urls/$code/analytics/export" -Headers (AuthHeaders $engToken)
    Assert-Eq 'CSV export disabled status' $csvOff.Status 409
    Assert-Eq 'CSV export disabled message' $csvOff.Json.message 'Analytics export is not enabled'
} else {
    $script:fail++
    $script:results.Add("FAIL  custom alias unexpected status=$($aliasTry.Status) body=$($aliasTry.Body)") | Out-Null
}

$del = Invoke-Curl DELETE "$base/api/v1/urls/$code" -Headers (AuthHeaders $engToken)
Assert-Eq "DELETE /api/v1/urls/$code status" $del.Status 204
Assert-True 'DELETE has empty body' ([string]::IsNullOrWhiteSpace($del.Body)) "body=[$($del.Body)]"

$afterDel = Invoke-Curl GET "$base/api/v1/urls/$code" -Headers (AuthHeaders $engToken)
Assert-Eq 'GET after deactivate status' $afterDel.Status 200
Assert-Eq 'GET after deactivate active=false' ([string]$afterDel.Json.active) 'False'

$gone = Invoke-Curl GET "$base/s/$code"
Assert-Eq 'redirect after deactivate status' $gone.Status 410
Assert-Eq 'redirect after deactivate message' $gone.Json.message 'Short URL is inactive'

$admin = Invoke-Curl POST "$base/api/v1/auth/login" -JsonBody '{"username":"admin","password":"Admin@123"}'
Assert-Eq 'admin login status' $admin.Status 200
Assert-True 'admin roles' ($admin.Json.roles -match 'ADMIN') $admin.Json.roles

Write-Host ''
Write-Host '========== URL SHORTENER API RESULTS =========='
$results | ForEach-Object { Write-Host $_ }
Write-Host '-----------------------------------------------'
Write-Host ("FEATURE_FLAGS_ON=" + $flagsOn)
Write-Host ("PASS=$pass FAIL=$fail TOTAL=$($pass+$fail)")
if ($fail -gt 0) { exit 1 } else { exit 0 }
