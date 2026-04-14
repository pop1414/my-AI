# 定义输入参数
param(
    [switch]$SkipBackend # 如果运行脚本时带上 -SkipBackend 参数，则只启动数据库，不启动后端服务
)

# 设置错误处理策略：一旦执行过程中出错（如 docker 启动失败），立即停止脚本
$ErrorActionPreference = "Stop"
# 获取项目根目录的绝对路径 ($PSScriptRoot 为当前脚本所在目录 infra/)
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

# 切换到项目根目录执行后续操作
Push-Location $repoRoot
try {
    Write-Host "[dev-up] Starting PostgreSQL + PGVector ..."
    # 使用 Docker Compose 在后台启动名为 postgres 的服务（包含 pgvector 扩展的数据库）
    docker compose -f infra/docker-compose.yml up -d postgres
    # 显示容器的运行状态，方便开发者确认
    docker compose -f infra/docker-compose.yml ps

    # 检查是否需要启动后端 Spring Boot 应用
    if (-not $SkipBackend) {
        Write-Host "[dev-up] Starting Spring Boot application ..."
        # 使用 Maven Wrapper 启动 Spring Boot 应用，确保 Maven 版本一致性
        .\mvnw.cmd spring-boot:run
    } else {
        # 如果指定了 -SkipBackend，则提示用户已跳过
        Write-Host "[dev-up] Skip backend startup because -SkipBackend is provided."
    }
} finally {
    # 无论脚本是否运行成功，最后都恢复到原来的目录位置
    Pop-Location
}
