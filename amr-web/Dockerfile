# ===== 第一階段：使用 Node 24 編譯前端靜態資源 =====
FROM node:24-alpine AS builder
WORKDIR /app

# 先安裝相依套件以善用 Docker 快取層
COPY package.json package-lock.json ./
RUN npm ci

# 複製原始碼並執行 TypeScript 檢查與 Vite 打包
COPY . .
RUN npm run build

# ===== 第二階段：使用輕量 Nginx 提供靜態網頁 =====
FROM nginx:alpine

# 複製自訂 Nginx 設定與編譯後的 dist 目錄
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 5173
CMD ["nginx", "-g", "daemon off;"]