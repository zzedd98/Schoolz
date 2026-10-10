# syntax=docker/dockerfile:1.7
FROM node:24.14.0-alpine AS build
WORKDIR /workspace
ARG BUILD_CONFIGURATION=production
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY . .
RUN npm run build -- --configuration "${BUILD_CONFIGURATION}"

FROM nginxinc/nginx-unprivileged:1.28-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist/frontend/browser /usr/share/nginx/html
USER 101
EXPOSE 8080
