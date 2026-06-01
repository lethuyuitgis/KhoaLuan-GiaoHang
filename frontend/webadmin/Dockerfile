# syntax=docker/dockerfile:1.7

FROM node:20-alpine AS builder

RUN corepack enable && corepack prepare pnpm@9.12.0 --activate

WORKDIR /workspace

COPY pnpm-workspace.yaml pnpm-lock.yaml package.json ./
COPY shared/package.json   shared/package.json
COPY miniapp/package.json  miniapp/package.json
COPY webadmin/package.json webadmin/package.json

RUN --mount=type=cache,target=/root/.local/share/pnpm/store \
    pnpm install --frozen-lockfile --filter @shop/webadmin... --filter @shop/shared

COPY shared/   shared/
COPY webadmin/ webadmin/

RUN pnpm --filter @shop/webadmin build

FROM nginx:alpine AS runtime

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

COPY --from=builder /workspace/webadmin/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=3 \
  CMD wget -q -O - http://127.0.0.1:80/healthz | grep -q ok || exit 1
