# AgentTo RAG 服务全链路接口验证：任何响应都不得为 500
# PS 5.1 调用外部程序会剥离参数中的双引号，所有 JSON body 一律写临时文件，用 -d @file 传递
$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:18473'
$results = @()
$curl = 'curl.exe'
$tmpDir = Join-Path $env:TEMP 'agentto-verify'
New-Item -ItemType Directory -Force -Path $tmpDir | Out-Null

function New-JsonFile {
    param([string]$Name, [string]$Json)
    $path = Join-Path $tmpDir ($Name + '.json')
    [System.IO.File]::WriteAllText($path, $Json, [System.Text.UTF8Encoding]::new($false))
    return $path
}

function Invoke-Check {
    param([string]$Name, [string[]]$CurlArgs)
    $out = & $curl -s -o NUL -w "%{http_code}" @CurlArgs 2>&1
    $status = 0
    try { $status = [int](($out -join '').Trim()) } catch { $status = 999 }
    $ok = ($status -ge 200 -and $status -lt 300) -or $status -eq 400 -or $status -eq 401 -or $status -eq 403 -or $status -eq 404 -or $status -eq 405 -or $status -eq 415 -or $status -eq 503
    $flag = if ($status -eq 500) { '!!! 500 !!!' } elseif ($ok) { 'PASS' } else { 'CHECK' }
    $script:results += [PSCustomObject]@{ Name = $Name; Status = $status; Result = $flag }
    Write-Host ("{0,-52} {1} {2}" -f $Name, $status, $flag)
}

function Invoke-CheckBody {
    param([string]$Name, [string[]]$CurlArgs)
    $raw = & $curl -s -w "`n%{http_code}" @CurlArgs 2>&1
    $joined = ($raw -join "`n")
    $lines = $joined -split "`r?`n"
    $status = 0
    try { $status = [int]($lines[-1].Trim()) } catch { $status = 999 }
    $content = ($lines[0..($lines.Length - 2)] -join "`n")
    $ok = ($status -ge 200 -and $status -lt 300) -or $status -eq 400 -or $status -eq 401 -or $status -eq 404 -or $status -eq 405 -or $status -eq 415 -or $status -eq 503
    $flag = if ($status -eq 500) { '!!! 500 !!!' } elseif ($ok) { 'PASS' } else { 'CHECK' }
    $script:results += [PSCustomObject]@{ Name = $Name; Status = $status; Result = $flag }
    Write-Host ("{0,-52} {1} {2}" -f $Name, $status, $flag)
    return $content
}

$loginJson = New-JsonFile 'login' '{"username":"admin","password":"admin123"}'
$badJson = New-JsonFile 'bad' '{"username":"admin","password":}'
$queryJson = New-JsonFile 'query' '{"query":"预算审查","keywordLimit":12,"vectorLimit":12,"fusionLimit":10,"rerankLimit":10,"finalLimit":8}'
$kbJson = New-JsonFile 'kb' '{"name":"联调验证知识库","description":"本地联调全链路验证","visibility":"PRIVATE"}'

# 1. 健康检查
Invoke-Check -Name 'GET /actuator/health' -CurlArgs @('-s', "$base/actuator/health")

# 2. 管理端登录（合法 JSON）
$loginBody = Invoke-CheckBody -Name 'POST /api/auth/login' -CurlArgs @('-s', '-X', 'POST', '-H', 'Content-Type: application/json', '-d', "@$loginJson", "$base/api/auth/login")
$session = $null
try { $session = ($loginBody | ConvertFrom-Json).data.token } catch {}

# 3. 登录接口非法 JSON → 400（不得 500）
Invoke-Check -Name 'POST /api/auth/login (malformed json)' -CurlArgs @('-s', '-X', 'POST', '-H', 'Content-Type: application/json', '-d', "@$badJson", "$base/api/auth/login")

# 4. 登录接口非 JSON Content-Type → 415（不得 500）
Invoke-Check -Name 'POST /api/auth/login (text/plain)' -CurlArgs @('-s', '-X', 'POST', '-H', 'Content-Type: text/plain', '-d', 'hello', "$base/api/auth/login")

# 5. 未认证访问管理端 → 401
Invoke-Check -Name 'GET /api/auth/me (no auth)' -CurlArgs @('-s', "$base/api/auth/me")

# 6. 公共查询无 Token → 401
Invoke-Check -Name 'POST /api/v1/rag/query (no token)' -CurlArgs @('-s', '-X', 'POST', '-H', 'Content-Type: application/json', '-d', '{"query":"测试"}', "$base/api/v1/rag/query")

# 7. 公共查询非法 Token → 401
Invoke-Check -Name 'POST /api/v1/rag/query (bad token)' -CurlArgs @('-s', '-X', 'POST', '-H', 'Content-Type: application/json', '-H', 'Authorization: Bearer rag_live_bad', '-d', '{"query":"测试"}', "$base/api/v1/rag/query")

# 8. 公共查询非法 JSON → 400（不得 500）
Invoke-Check -Name 'POST /api/v1/rag/query (malformed json)' -CurlArgs @('-s', '-X', 'POST', '-H', 'Content-Type: application/json', '-H', 'Authorization: Bearer rag_live_bad', '-d', '{"query":', "$base/api/v1/rag/query")

# 9. 不存在的路径 → 404（不落 500）
Invoke-Check -Name 'GET /api/admin/does-not-exist (404)' -CurlArgs @('-s', "$base/api/admin/does-not-exist")

# 10. 方法不匹配 → 405（不落 500）
Invoke-Check -Name 'GET /api/admin/knowledge-bases (405)' -CurlArgs @('-s', "$base/api/admin/knowledge-bases")

if ($session) {
    $auth = "Authorization: Bearer $session"

    # 11. 当前用户
    Invoke-Check -Name 'GET /api/auth/me' -CurlArgs @('-s', '-H', $auth, "$base/api/auth/me")

    # 12. Dashboard 概览（前端真实调用 /admin/dashboard）
    Invoke-Check -Name 'GET /api/admin/dashboard' -CurlArgs @('-s', '-H', $auth, "$base/api/admin/dashboard")

    # 13. 文档列表
    Invoke-Check -Name 'GET /api/admin/documents?page=0&size=20' -CurlArgs @('-s', '-H', $auth, "$base/api/admin/documents?page=0&size=20")

    # 14. 检索 Trace 列表
    Invoke-Check -Name 'GET /api/admin/retrieval/traces?limit=30' -CurlArgs @('-s', '-H', $auth, "$base/api/admin/retrieval/traces?limit=30")

    # 15. 管理端检索（真实 ES 链路）
    Invoke-Check -Name 'POST /api/admin/retrieval/search' -CurlArgs @('-s', '-X', 'POST', '-H', $auth, '-H', 'Content-Type: application/json', '-d', "@$queryJson", "$base/api/admin/retrieval/search")

    # 16. 检索 Job 创建与查询
    $jobBody = Invoke-CheckBody -Name 'POST /api/admin/retrieval/jobs' -CurlArgs @('-s', '-X', 'POST', '-H', $auth, '-H', 'Content-Type: application/json', '-d', "@$queryJson", "$base/api/admin/retrieval/jobs")
    try {
        $jobUid = ($jobBody | ConvertFrom-Json).data.jobUid
        if ($jobUid) {
            Invoke-Check -Name "GET /api/admin/retrieval/jobs/$jobUid" -CurlArgs @('-s', '-H', $auth, "$base/api/admin/retrieval/jobs/$jobUid")
        }
    } catch {}

    # 17. 创建知识库（POST 真实接口）
    Invoke-Check -Name 'POST /api/admin/knowledge-bases (create)' -CurlArgs @('-s', '-X', 'POST', '-H', $auth, '-H', 'Content-Type: application/json', '-d', "@$kbJson", "$base/api/admin/knowledge-bases")
} else {
    Write-Output '!!! 管理端登录失败，跳过管理端接口验证 !!!'
}

$fails = $results | Where-Object { $_.Status -eq 500 }
Write-Output ''
if ($fails) {
    Write-Output "=== 发现 $($fails.Count) 个 500 响应 ==="
    $fails | Format-Table -AutoSize
    exit 1
} else {
    Write-Output "=== 全链路验证完成：共 $($results.Count) 个接口，无任何 500 响应 ==="
    exit 0
}
