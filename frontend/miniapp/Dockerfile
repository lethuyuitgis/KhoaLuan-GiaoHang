# syntax=docker/dockerfile:1.7

# ─────────────────────────────────────────────────────────────────
# Stage 1: pnpm install + Vite build
# ─────────────────────────────────────────────────────────────────
FROM node:20-alpine AS builder

# Enable corepack (Node 20+) and pin pnpm major to match pnpm-lock.yaml.
RUN corepack enable && corepack prepare pnpm@9.12.0 --activate

WORKDIR /workspace

# Workspace metadata first — layer caches the dependency install when
# package.json / pnpm-lock.yaml don't change.
COPY pnpm-workspace.yaml pnpm-lock.yaml package.json ./
COPY shared/package.json   shared/package.json
COPY miniapp/package.json  miniapp/package.json
COPY webadmin/package.json webadmin/package.json

# --frozen-lockfile fails the build if pnpm-lock.yaml drifted from package.json
# in any workspace member. We --filter to skip webadmin's deps when building miniapp
# (saves ~80 MB of downloads).
RUN --mount=type=cache,target=/root/.local/share/pnpm/store \
    pnpm install --frozen-lockfile --filter @shop/miniapp... --filter @shop/shared

# Sources for the workspaces we actually need.
COPY shared/  shared/
COPY miniapp/ miniapp/

# Build — output goes to miniapp/dist/.
RUN pnpm --filter @shop/miniapp build

# ─────────────────────────────────────────────────────────────────
# Stage 2: nginx serves the static bundle
# ─────────────────────────────────────────────────────────────────
FROM nginx:alpine AS runtime

# SPA fallback config: any unknown path → index.html so React Router works.
RUN printf '%s\n' \
  'server {' \
  '    listen 80;' \
  '    server_name _;' \
  '    root /usr/share/nginx/html;' \
  '    index index.html;' \
  '    location / {' \
  '        try_files $uri $uri/ /index.html;' \
  '    }' \
  '    location = /healthz { return 200 "ok\n"; add_header Content-Type text/plain; }' \
  '}' > /etc/nginx/conf.d/default.conf

COPY --from=builder /workspace/miniapp/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=3 \
  CMD wget -q -O - http://127.0.0.1:80/healthz | grep -q ok || exit 1
