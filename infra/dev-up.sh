#!/usr/bin/env bash
# 设置 Bash 脚本的安全选项：
# -e: 命令执行失败时立即退出
# -u: 遇到未定义的变量时视为错误并退出
# -o pipefail: 管道中的任何一个环节出错，整个管道命令即视为失败
set -euo pipefail

# 获取脚本所在的目录 (infra/)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# 获取项目根目录的绝对路径
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# 切换工作目录到项目根目录
cd "${REPO_ROOT}"

echo "[dev-up] Starting PostgreSQL + PGVector ..."
# 调用 Docker Compose 在后台启动名为 postgres 的服务
docker compose -f infra/docker-compose.yml up -d postgres
# 打印当前容器的状态列表
docker compose -f infra/docker-compose.yml ps

# 检查命令行第一个参数是否为 --skip-backend
if [[ "${1:-}" == "--skip-backend" ]]; then
  # 如果用户提供了该参数，则仅启动数据库环境
  echo "[dev-up] Skip backend startup because --skip-backend is provided."
else
  # 默认启动后端 Spring Boot 应用
  echo "[dev-up] Starting Spring Boot application ..."
  # 通过 Maven Wrapper 运行应用
  ./mvnw spring-boot:run
fi
