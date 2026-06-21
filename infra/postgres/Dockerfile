# 1. 使用官方已编译好 pgvector 的 Postgres 16 镜像作为基底
FROM pgvector/pgvector:pg16

ENV DEBIAN_FRONTEND=noninteractive

# 2. 安装编译中文分词插件所需的系统依赖（新增 wget 和 bzip2 两个工具）
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    git \
    ca-certificates \
    postgresql-server-dev-16 \
    wget \
    bzip2 \
    && rm -rf /var/lib/apt/lists/*

# 3. 编译并安装 SCWS (使用官方发布的 tar.bz2 源码包，避免在本地重新生成构建脚本导致 automake 兼容性报错)
RUN wget -q -O - http://www.xunsearch.com/scws/down/scws-1.2.3.tar.bz2 | tar xjf - \
    && cd scws-1.2.3 \
    && ./configure \
    && make \
    && make install \
    && cd .. \
    && rm -rf scws-1.2.3

# 4. 编译并安装 zhparser 插件
RUN git clone --depth 1 https://github.com/amutu/zhparser.git /tmp/zhparser \
    && cd /tmp/zhparser \
    && make \
    && make install \
    && rm -rf /tmp/zhparser

# 5. 清理编译依赖，减小最终镜像体积
RUN apt-get purge -y build-essential git postgresql-server-dev-16 wget bzip2 \
    && apt-get autoremove -y \
    && apt-get clean

# 6. 更新动态链接库，确保 PostgreSQL 能够正常加载 scws 共享库
RUN ldconfig
