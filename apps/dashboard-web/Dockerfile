# ── Build stage ───────────────────────────────────────────────────────────────
FROM node:20-alpine AS build

WORKDIR /app
COPY apps/dashboard-web/package.json ./
RUN npm install

COPY apps/dashboard-web/ ./
RUN npm run build

# ── Serve stage ───────────────────────────────────────────────────────────────
FROM nginx:1.27-alpine AS serve

COPY --from=build /app/dist /usr/share/nginx/html

# Single-page app fallback — send all routes to index.html
RUN printf 'server {\n\
  listen 80;\n\
  root /usr/share/nginx/html;\n\
  index index.html;\n\
  location / {\n\
    try_files $uri $uri/ /index.html;\n\
  }\n\
  # Forward /v1 API calls to the API service\n\
  location /v1/ {\n\
    proxy_pass http://api:8000;\n\
    proxy_set_header Host $host;\n\
  }\n\
}\n' > /etc/nginx/conf.d/default.conf

EXPOSE 80
