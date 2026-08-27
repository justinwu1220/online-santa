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

## 本地開發的身分模擬

正式環境以 Firebase ID token 認證。本機開發啟用 `dev-auth` profile（由
`spring.profiles.group` 在 `local` 時自動帶出），可用 `X-Dev-User-Email` 標頭
指定身分，免去每次 curl 都要先跑完瀏覽器登入流程：

```bash
curl http://localhost:8080/api/me -H "X-Dev-User-Email: org@example.org"
```

第一次出現的 email 會自動建立為 `DONOR` 帳號。帶了 `Authorization` 標頭的請求
會跳過這個機制，真實 token 永遠優先。

`dev-auth` 與 `prod` 不可同時啟用，`AuthConfigurationGuard` 會在啟動時檢查，
組合錯誤就讓應用程式起不來。

### 成為管理員

把 email 加進白名單，下次登入即取得 `ADMIN` 角色：

```bash
APP_ADMIN_EMAILS=you@example.com ./mvnw spring-boot:run
```

管理員可在 `/api/admin/organizations` 審核機構註冊。已隸屬機構的帳號不會被提升，
避免同一人既能上架願望又能審核自己的機構。

> **Windows 使用者注意**：Git Bash 把命令列參數傳給 `curl.exe` 時會破壞 UTF-8，
> 含中文的 JSON 請寫成檔案再以 `--data-binary @body.json` 送出。

## 正式環境設定

| 環境變數 | 說明 |
|---|---|
| `FIREBASE_PROJECT_ID` | Firebase 專案 ID，用於推導 ID token 的 issuer 與 audience |
| `APP_ADMIN_EMAILS` | 管理員白名單，逗號分隔 |
| `APP_ALLOWED_ORIGINS` | 允許跨來源請求的前端網域 |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | Neon PostgreSQL 連線資訊 |

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
