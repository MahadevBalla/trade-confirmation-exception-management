# Multi-stage: build static assets with Node, then serve with pinned Nginx.
#
# Phase 3 DevOps hardening (feat/devops-infra):
#   - Pinned base images: node:20-alpine, nginx:1.27-alpine
#   - npm ci (reproducible install from lock file) instead of npm install
#   - File ownership hardened for nginx user
#   - HEALTHCHECK instruction baked in

# ------------------------------------------------------------------------------
# Stage 1: Build static distribution
# ------------------------------------------------------------------------------
FROM node:20-alpine AS build
WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build

# ------------------------------------------------------------------------------
# Stage 2: Serve with Nginx
# ------------------------------------------------------------------------------
FROM nginx:1.27-alpine AS runtime

COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Security: ensure nginx owns its served files
RUN chown -R nginx:nginx /usr/share/nginx/html && \
    chmod -R 755 /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=10s --timeout=3s --retries=3 \
  CMD wget -qO- http://localhost:80/ > /dev/null 2>&1 || exit 1

CMD ["nginx", "-g", "daemon off;"]
