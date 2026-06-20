# ============================================================
# 文档 ID 映射导出脚本
# 用途：登录后查询 default 知识库的文档列表，输出
#       filename → documentId 映射，方便填写 retrieval-qa-pairs.json
# 用法：pwsh src/test/resources/eval/export-doc-mapping.ps1
# ============================================================

param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Username = "admin",
    [string]$Password = "admin",
    [string]$KbId = "default",
    [int]$Limit = 100
)

$ErrorActionPreference = "Stop"

# --- Step 1: 登录 ---
Write-Host "[1/3] 登录中..." -ForegroundColor Cyan

if (-not $Password) {
    $Password = Read-Host -Prompt "请输入密码"
}

$loginBody = @{ username = $Username; password = $Password } | ConvertTo-Json
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession

try {
    $loginResp = Invoke-RestMethod `
        -Uri "$BaseUrl/api/v1/auth/login" `
        -Method POST `
        -ContentType "application/json" `
        -Headers @{ "X-MYAI-CSRF" = "1" } `
        -Body $loginBody `
        -WebSession $session
    Write-Host "  登录成功: $($loginResp.username)" -ForegroundColor Green
} catch {
    Write-Host "  登录失败: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# --- Step 2: 查询文档列表 ---
Write-Host "[2/3] 查询知识库 [$KbId] 的文档列表..." -ForegroundColor Cyan

$docs = Invoke-RestMethod `
    -Uri "$BaseUrl/api/v1/documents?kbId=$KbId&limit=$Limit" `
    -Headers @{ "X-MYAI-CSRF" = "1" } `
    -WebSession $session

$total = $docs.items.Count
if ($total -eq 0) {
    Write-Host "  未找到任何文档，请确认知识库 [$KbId] 中已有入库文档" -ForegroundColor Yellow
    exit 0
}

Write-Host "  找到 $total 篇文档" -ForegroundColor Green

# --- Step 3: 输出映射表 ---
Write-Host "[3/3] 文档映射表（用于替换 retrieval-qa-pairs.json 中的占位名）:" -ForegroundColor Cyan
Write-Host ""

# 表头
Write-Host ("  {0,-40} → documentId" -f "filename") -ForegroundColor DarkGray
Write-Host ("  {0}" -f ("-" * 80)) -ForegroundColor DarkGray

foreach ($doc in $docs.items) {
    $status = if ($doc.status -eq "INDEXED") { "OK" } else { $doc.status }
    Write-Host ("  {0,-40} → {1}  [{2}]" -f $doc.filename, $doc.documentId, $status)
}

Write-Host ""
Write-Host "将上面的 documentId 替换到 retrieval-qa-pairs.json 中的 relevant_doc_ids 字段即可。" -ForegroundColor Yellow

# --- 可选：导出 JSON 映射文件 ---
$mappingFile = Join-Path $PSScriptRoot "doc-id-mapping.json"
$mapping = @{}
foreach ($doc in $docs.items) {
    $mapping[$doc.filename] = @{
        documentId = $doc.documentId
        status     = $doc.status
    }
}
$mapping | ConvertTo-Json -Depth 3 | Out-File -Encoding utf8 $mappingFile
Write-Host "映射已导出到: $mappingFile" -ForegroundColor Green
