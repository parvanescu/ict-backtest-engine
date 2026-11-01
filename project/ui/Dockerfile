# docker build -t alert-service-ui .     (normally built by ../compose.yaml)
# The browser runs on the host, so the service address baked in must be the one the host sees.

FROM node:24-alpine AS build
WORKDIR /ui
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
ARG VITE_ALERT_SERVICE_HTTP=http://127.0.0.1:8080
ARG VITE_ALERT_SERVICE_WS=ws://127.0.0.1:8080/ws
RUN npm run build

FROM nginx:1.30-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /ui/dist /usr/share/nginx/html
EXPOSE 80
