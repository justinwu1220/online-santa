# 線上聖誕老公公 (Online Santa)

讓兒童機構上架孩童的聖誕願望，一般大眾認領並送禮，形成
**願望發布 → 認領 → 寄送 → 追蹤回饋** 的完整閉環。

## 架構

```
使用者 / 機構
     │
     ▼
Cloud Run (Spring Boot API)   ── scale-to-zero，離峰 $0
     ├── Neon (PostgreSQL)     ── 願望、認領、追蹤狀態
     ├── Cloud Storage         ── 照片（前端 Signed URL 直傳）
     └── Firebase Auth         ── 買家 / 賣家 / 管理員認證
```

前端 (React + Vite) 部署於 Firebase Hosting。

## 技術棧

| 層 | 技術 |
|---|---|
| 後端 | Java 21 · Spring Boot 3.5 · Spring Data JPA · Spring Security · Flyway |
| 資料庫 | PostgreSQL 16（本地 Docker / 正式 Neon） |
| 前端 | React 19 · TypeScript · Vite · TanStack Query · Tailwind CSS |
| 測試 | JUnit 5 · Testcontainers |
| CI/CD | GitHub Actions → Artifact Registry → Cloud Run |

## 本地開發

```bash
# 1. 啟動資料庫
docker compose up -d

# 2. 啟動後端（http://localhost:8080）
cd backend && ./mvnw spring-boot:run

# 3. 啟動前端（http://localhost:5173）
cd frontend && npm install && npm run dev
```

健康檢查：`curl http://localhost:8080/actuator/health`
API 文件：<http://localhost:8080/swagger-ui.html>

## 專案結構

```
backend/    Spring Boot API（package-by-feature）
frontend/   React + Vite 前端
docs/       架構決策紀錄、隱私規範、部署手冊
```

## 設計重點

- **搶領防超賣**：認領走原子條件 UPDATE（樂觀鎖），並以 partial unique index 作為資料庫層最後防線
- **兒童隱私**：schema 層級不存在孩童姓名 / 生日 / 照片欄位，僅有暱稱與年齡區間
- **成本控制**：全鏈路 scale-to-zero，離峰帳單接近 $0
