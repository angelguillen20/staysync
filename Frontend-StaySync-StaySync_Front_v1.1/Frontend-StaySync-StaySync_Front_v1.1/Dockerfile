# ─────────────────────────────────────────────────────────────────────────────
# StaySync — frontend (React 18 + Vite)  |  Puerto 80
# ─────────────────────────────────────────────────────────────────────────────

# ── Etapa 1: Build con Node ───────────────────────────────────────────────────
FROM node:20-alpine AS build

WORKDIR /app

COPY package*.json ./
RUN npm ci --silent

COPY . .
RUN npm run build

# ── Etapa 2: Servidor Nginx (solo archivos estáticos) ─────────────────────────
FROM nginx:1.27-alpine AS runtime

# Configuración SPA con fallback a index.html para React Router
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copiar el build de Vite (siempre genera en /dist)
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -qO- http://localhost/health || exit 1

CMD ["nginx", "-g", "daemon off;"]
