# =============================================================================
# Halo frontend — Dockerfile dev/staging (multi-stage)
#
# Build do PWA via Vite e serve estaticamente com nginx.
# Versão produção otimizada (compressão, headers de cache, CSP) entra em T-046.
# =============================================================================

# ---------- Stage 1: build ----------
FROM node:20-alpine AS build
WORKDIR /workspace

# Build-time: URL da API que o JS bundlado vai chamar.
# É a URL acessada pelo BROWSER do usuário (fora do compose),
# então http://localhost:8080 quando rodando localmente no docker.
ARG VITE_API_BASE_URL=http://localhost:8080
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# ---------- Stage 2: nginx ----------
FROM nginx:1.27-alpine
COPY --from=build /workspace/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
HEALTHCHECK --interval=15s --timeout=3s --start-period=5s --retries=3 \
    CMD wget --quiet --spider http://localhost/ || exit 1
