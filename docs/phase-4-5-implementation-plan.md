# Implementation Plan: Phase 4 & Phase 5 (DevOps Infrastructure)

This document provides a complete, step-by-step engineering blueprint for implementing **Phase 4 (Nginx Reverse Proxy / API Gateway)** and **Phase 5 (CI/CD Pipeline Automation)**.

---

## Part 1: Phase 4 — Nginx Reverse Proxy / Unified API Gateway

### 1.1 Objective & Context
Currently, backend microservices listen on separate host ports (`8081` to `8085`). If the frontend attempts to make API calls to these ports directly, it will encounter:
- Cross-Origin Resource Sharing (CORS) rejections.
- Configuration coupling (the client app must track 5 different base URLs).

**Phase 4 Goal:** Configure Nginx inside the `frontend` container (listening on port `80`, mapped to host port `3000`) to act as a unified API gateway. All frontend requests go to relative paths (`/api/*`), and Nginx proxies them to the appropriate container over `tcem-network`.

```
Browser (http://localhost:3000)
    │
    ▼
┌────────────────────────────────────────────────────────────────────────┐
│ Nginx Gateway (frontend container on port 80)                          │
│                                                                        │
│  /api/trades/*      ──► http://ingestion-service:8081/trades/*         │
│  /api/metrics/*     ──► http://ingestion-service:8081/metrics/*        │
│  /api/enrichment/*  ──► http://enrichment-service:8082/enrichment/*    │
│  /api/validation/*  ──► http://validation-service:8083/*               │
│  /api/exceptions/*  ──► http://exception-service:8084/exceptions/*     │
│  /api/workitems/*   ──► http://workitem-service:8085/workitems/*       │
│                                                                        │
│  /*                 ──► /usr/share/nginx/html/index.html (SPA fallback)│
└────────────────────────────────────────────────────────────────────────┘
```

---

### 1.2 File Changes Required for Phase 4

#### File 1: `frontend/nginx.conf`
Replace the minimal placeholder in `frontend/nginx.conf` with the full reverse proxy configuration:

```nginx
server {
    listen 80;
    server_name localhost;

    # Docker internal DNS resolver (resolves hostnames dynamically on demand)
    resolver 127.0.0.11 valid=30s ipv6=off;

    gzip on;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml application/xml+rss text/javascript;

    # Ingestion APIs: /api/trades -> ingestion-service:8081/trades
    location ~ ^/api/trades(?<rest>/.*)?$ {
        set $ingestion_upstream http://ingestion-service:8081;
        proxy_pass $ingestion_upstream/trades$rest$is_args$args;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 5s;
        proxy_read_timeout 60s;
    }

    # Ingestion APIs: /api/metrics -> ingestion-service:8081/metrics
    location ~ ^/api/metrics(?<rest>/.*)?$ {
        set $ingestion_upstream http://ingestion-service:8081;
        proxy_pass $ingestion_upstream/metrics$rest$is_args$args;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # Enrichment Service APIs
    location ~ ^/api/enrichment(?<rest>/.*)?$ {
        set $enrichment_upstream http://enrichment-service:8082;
        proxy_pass $enrichment_upstream/enrichment$rest$is_args$args;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # Validation & Confirmation Service APIs (root-mounted)
    location ~ ^/api/validation(?<rest>/.*)?$ {
        set $validation_upstream http://validation-service:8083;
        proxy_pass $validation_upstream$rest$is_args$args;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # Exception Service APIs
    location ~ ^/api/exceptions(?<rest>/.*)?$ {
        set $exception_upstream http://exception-service:8084;
        proxy_pass $exception_upstream/exceptions$rest$is_args$args;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # Work Item & DLQ Replay Service APIs
    location ~ ^/api/workitems(?<rest>/.*)?$ {
        set $workitem_upstream http://workitem-service:8085;
        proxy_pass $workitem_upstream/workitems$rest$is_args$args;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # Single Page Application (SPA) routing fallback
    location / {
        root /usr/share/nginx/html;
        index index.html index.htm;
        try_files $uri $uri/ /index.html;
    }
}
```

#### File 2 (Optional Dev Quality-of-Life): `frontend/vite.config.ts`
For local development outside of Docker (running `npm run dev` on port `5173`), add a proxy block so the React dev server mirrors Nginx:

```typescript
import react, { reactCompilerPreset } from '@vitejs/plugin-react'
import babel from '@rolldown/plugin-babel'
import { defineConfig } from 'vite'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [
    react(),
    babel({ presets: [reactCompilerPreset()] }),
    tailwindcss(),
  ],
  server: {
    proxy: {
      '/api/trades': { target: 'http://localhost:8081', changeOrigin: true, rewrite: (path) => path.replace(/^\/api/, '') },
      '/api/metrics': { target: 'http://localhost:8081', changeOrigin: true, rewrite: (path) => path.replace(/^\/api/, '') },
      '/api/enrichment': { target: 'http://localhost:8082', changeOrigin: true, rewrite: (path) => path.replace(/^\/api/, '') },
      '/api/validation': { target: 'http://localhost:8083', changeOrigin: true, rewrite: (path) => path.replace(/^\/api\/validation/, '') },
      '/api/exceptions': { target: 'http://localhost:8084', changeOrigin: true, rewrite: (path) => path.replace(/^\/api/, '') },
      '/api/workitems': { target: 'http://localhost:8085', changeOrigin: true, rewrite: (path) => path.replace(/^\/api/, '') },
    },
  },
})
```

---

### 1.3 Step-by-Step Verification for Phase 4

1. **Rebuild and restart the frontend container**:
   ```bash
   docker compose up -d --build frontend
   ```
2. **Verify Nginx container is running and healthy**:
   ```bash
   docker compose ps frontend
   ```
3. **Verify SPA static file delivery on port 3000**:
   ```bash
   curl -I http://localhost:3000/
   # Expected: HTTP/1.1 200 OK, Content-Type: text/html
   ```
4. **Verify Gateway Proxy routing to backend**:
   ```bash
   curl -s http://localhost:3000/api/trades/scenarios | jq .
   # Expected: Returns the 10 scenario strings from ingestion-service:8081
   
   curl -s http://localhost:3000/api/metrics/stp-rate
   # Expected: Returns STP rate metric JSON
   ```
5. **Verify SPA Client-Side Fallback Routing**:
   ```bash
   curl -I http://localhost:3000/dashboard/exceptions
   # Expected: HTTP/1.1 200 OK (serves index.html instead of 404)
   ```

---

## Part 2: Phase 5 — CI/CD Pipeline Automation (GitHub Actions)

### 2.1 Objective & Context
Currently, GitHub Actions workflows only run basic compilation tests.
**Phase 5 Goal:** Harden CI/CD pipelines to:
1. Verify Java compilation across all reactor modules and run unit tests.
2. Verify Docker image building for all backend microservices using BuildKit caching.
3. Verify Frontend build, lint, and Nginx container packaging.
4. (Optional) Run an automated containerized smoke test on Pull Requests to `main`.

---

### 2.2 File Changes Required for Phase 5

#### File 1: `.github/workflows/backend-ci.yml`
Update the backend CI workflow to test both Maven verification and Docker container build viability:

```yaml
name: backend-ci

on:
  push:
    branches: [main, feat/*]
    paths:
      - "services/**"
      - ".github/workflows/backend-ci.yml"
  pull_request:
    paths:
      - "services/**"
      - ".github/workflows/backend-ci.yml"

jobs:
  test:
    name: Maven Reactor Build & Tests
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "21"
          cache: maven

      - name: Build & test all services
        working-directory: services
        run: mvn -B -V verify

  docker-build:
    name: Docker Image Build Verification
    needs: test
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Build all backend service containers
        run: |
          docker compose build ingestion-service enrichment-service validation-service exception-service workitem-service
```

#### File 2: `.github/workflows/frontend-ci.yml`
Update frontend CI to use `npm ci` (deterministic lockfile install) and verify Nginx container packaging:

```yaml
name: frontend-ci

on:
  push:
    branches: [main, feat/*]
    paths:
      - "frontend/**"
      - ".github/workflows/frontend-ci.yml"
  pull_request:
    paths:
      - "frontend/**"
      - ".github/workflows/frontend-ci.yml"

jobs:
  build:
    name: Frontend Lint & Build
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: frontend
    steps:
      - uses: actions/checkout@v4

      - name: Set up Node
        uses: actions/setup-node@v4
        with:
          node-version: "20"
          cache: npm
          cache-dependency-path: frontend/package-lock.json

      - name: Install dependencies (clean install)
        run: npm ci

      - name: Lint
        run: npm run lint

      - name: Build static distribution
        run: npm run build

  docker-build:
    name: Frontend Docker Image Build
    needs: build
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Build frontend container image
        run: docker compose build frontend
```

#### File 3 (New): `.github/workflows/smoke-test.yml`
Runs on Pull Requests to `main` to boot the stack and verify that cold startup succeeds:

```yaml
name: e2e-smoke-test

on:
  pull_request:
    branches: [main]

jobs:
  smoke-test:
    name: Cold Boot & Smoke Test
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Setup Environment
        run: cp .env.example .env

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Build and Start Infrastructure
        run: |
          docker compose up -d kafka couchdb kafka-ui
          docker compose up kafka-init couchdb-init

      - name: Build and Start Services
        run: |
          docker compose up -d --build ingestion-service frontend

      - name: Verify Container Health
        run: |
          sleep 15
          docker compose ps
          
          echo "=== Testing Ingestion Health ==="
          curl -f http://localhost:8081/actuator/health || exit 1
          
          echo "=== Testing Gateway Proxy ==="
          curl -f http://localhost:3000/api/trades/scenarios || exit 1

      - name: Dump Logs on Failure
        if: failure()
        run: docker compose logs

      - name: Cleanup
        if: always()
        run: docker compose down -v
```

---

### 2.3 Step-by-Step Verification for Phase 5

1. **Test commands locally before pushing**:
   ```bash
   # Test backend verification
   cd services && mvn -B -V verify && cd ..
   
   # Test frontend lint and build
   cd frontend && npm ci && npm run lint && npm run build && cd ..
   
   # Test Docker builds
   docker compose build
   ```
2. **Commit and push changes to feature branch**:
   ```bash
   git add .github/workflows/
   git commit -m "feat(devops): Phase 5 - add multi-stage container builds and smoke test workflows to CI"
   git push origin feat/devops-infra
   ```
3. **Verify in GitHub Actions UI**:
   - Confirm all 3 workflow jobs pass green on GitHub.

---

## Execution Checklist for Developer

- [ ] **Phase 4**:
  - [ ] Edit `frontend/nginx.conf` with microservice reverse proxy rules.
  - [ ] (Optional) Add dev proxy block in `frontend/vite.config.ts`.
  - [ ] Run `docker compose up -d --build frontend`.
  - [ ] Verify `curl http://localhost:3000/api/trades/scenarios` returns 200 and JSON.
- [ ] **Phase 5**:
  - [ ] Update `.github/workflows/backend-ci.yml` (add Docker build step).
  - [ ] Update `.github/workflows/frontend-ci.yml` (switch to `npm ci` and add Docker build step).
  - [ ] Create `.github/workflows/smoke-test.yml`.
  - [ ] Push to GitHub and observe CI workflow execution.
