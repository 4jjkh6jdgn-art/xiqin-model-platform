FROM node:22-slim

# 可选构建代理。服务器未提供参数时不使用代理，避免依赖旧开发机地址。
ARG HTTP_PROXY
ARG HTTPS_PROXY
ARG NO_PROXY=localhost,127.0.0.1,registry.npmmirror.com,mirrors.aliyun.com

# 替换 Debian 源为阿里云镜像加速（apt 直连国内，避免海外源慢/断）
RUN sed -i 's|deb.debian.org|mirrors.aliyun.com|g' /etc/apt/sources.list.d/debian.sources 2>/dev/null; \
    sed -i 's|deb.debian.org|mirrors.aliyun.com|g' /etc/apt/sources.list 2>/dev/null; \
    rm -rf /var/lib/apt/lists/*

# 安装构建与运行依赖（用于 gl / canvas 原生模块；python-is-python3 提供 node-gyp 需要的 python 命令）
RUN apt-get \
      -o Acquire::Retries=10 \
      -o Acquire::Queue-Mode=access \
      -o Acquire::http::Pipeline-Depth=0 \
      -o Acquire::https::Pipeline-Depth=0 \
      update \
    && apt-get \
      -o Acquire::Retries=10 \
      -o Acquire::Queue-Mode=access \
      -o Acquire::http::Pipeline-Depth=0 \
      -o Acquire::https::Pipeline-Depth=0 \
      install -y --no-install-recommends \
    python3 \
    python-is-python3 \
    make \
    g++ \
    pkg-config \
    # headless-gl (OpenGL) + Xvfb（无显示环境下提供 X server，headless-gl 依赖它创建 WebGL 上下文）
    xvfb \
    libgl1-mesa-dev \
    libglu1-mesa-dev \
    libxi-dev \
    libxmu-dev \
    libxext-dev \
    libxt-dev \
    # node-canvas
    libcairo2-dev \
    libpango1.0-dev \
    libjpeg-dev \
    libgif-dev \
    librsvg2-dev \
    # 运行时库
    libgl1 \
    libglu1-mesa \
    libxi6 \
    libxext6 \
    libxtst6 \
    libxtst6 \
    && rm -rf /var/lib/apt/lists/*
# 压缩包解压工具（可选，安装失败不影响非压缩包模型处理）
RUN apt-get update && apt-get install -y --no-install-recommends p7zip-full 2>/dev/null || echo "p7zip-full unavailable, archive extraction disabled"

WORKDIR /app

# 先复制依赖清单以利用 Docker 层缓存
COPY package.json package-lock.json* ./
RUN npm install --registry https://registry.npmmirror.com --dist-url=https://npmmirror.com/mirrors/node

COPY . .

# 启动脚本：先拉起虚拟 X server（供 headless-gl 渲染真实缩略图），再启动 worker 主进程
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

ENTRYPOINT ["/entrypoint.sh"]
CMD ["node", "--max-old-space-size=4096", "src/index.js"]
