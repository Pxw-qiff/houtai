FROM node:20-alpine AS builder

ARG VITE_NEW_API_WEB_URL=http://localhost:3000
ENV VITE_NEW_API_WEB_URL=$VITE_NEW_API_WEB_URL

WORKDIR /build
COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build

FROM nginx:1.27-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /build/dist /usr/share/nginx/html

EXPOSE 80