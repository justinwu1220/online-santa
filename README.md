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

## 三個入口

三個在使用者感受上獨立的網站，共用同一套程式碼與身分系統：

| | 路徑 | 進入方式 |
|---|---|---|
| **主網站** | `/` | 願望牆公開瀏覽，右上角登入後可認領與查看紀錄 |
| **機構後台** | `/org` | 主網站頁尾的「我是兒童機構」進入，導覽列**不含願望牆** |
| **監控中心** | `/admin` | 任何地方都沒有連結，直接輸入網址 |

> 監控中心不列出入口只是減少誤闖，**網址不是安全邊界** —— 真正的保護是後端的
> `hasRole('ADMIN')` 與本地 `users` 表的角色。

一個帳號只能有一種角色（資料庫的 `ck_users_org_membership` 強制這點）。機構登入頁
遇到一般民眾的帳號時會顯示申請表單，並警告成為機構成員後將無法再以個人身分認領。

### 管理員的稽核軌跡

管理員為了處理申訴需要跨機構檢視，看得到捐贈者個資與含孩童影像的回饋照片。
「能看」與「看了不留痕跡」是兩件事——每次存取單筆認領詳情或附件都寫入
`admin_audit_logs`，且這份紀錄在 `/admin/system` 對所有管理員公開。

清單頁不寫稽核：它只顯示彙總與流程狀態，每次翻頁都記會把真正重要的紀錄淹沒。

## 前端

React 19 + TypeScript + Vite + Tailwind v4，涵蓋願望牆、認領流程、機構後台與審核後台。

身分驗證有兩種模式，依 Firebase 設定是否齊全**自動選擇**，與後端的 `dev-auth` 對稱：

| 模式 | 啟用條件 | 請求標頭 |
|---|---|---|
| Firebase | 設定了 `VITE_FIREBASE_API_KEY` 與 `VITE_FIREBASE_PROJECT_ID` | `Authorization: Bearer <ID token>` |
| 開發 | 未設定 Firebase | `X-Dev-User-Email` |

開發模式讓整個專案不需要任何雲端資源就能跑起來——`npm run dev` 後在右上角輸入
任意 email 即可切換身分。Firebase SDK 以動態 import 載入，沒設定時不會進入打包結果。

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

## 圖片上傳

依敏感度分成兩個 bucket：

| Bucket | 內容 | 存取方式 |
|---|---|---|
| 公開 | 禮物示意圖 | 固定網址，無需簽章 |
| 私密 | 寄送證明、送禮回饋照片 | 限時簽章網址（10 分鐘） |

上傳走三步驟，檔案不經過 API：

```
POST /api/uploads/signed-url   → 取得限時直傳網址
PUT  <該網址>                   → 前端直接傳到儲存端
POST /api/attachments/{id}/confirm → 後端向儲存端查證後標記完成
```

本機開發啟用 `dev-storage` profile（隨 `local` 自動帶出），檔案存在
`backend/.local-storage/`，不需要 GCP 專案就能跑完整個流程。

隱私規範見 [docs/PRIVACY.md](docs/PRIVACY.md)。

## 逾期認領的處理

去年願望供不應求，認領後遲遲未寄送會讓孩子的願望一直卡著。是否收回由**機構自己
決定**，設定在 `PATCH /api/organizations/me`：

| 政策 | 逾期時的行為 |
|---|---|
| `MANUAL` | 只列入機構後台的逾期清單（`GET /api/organizations/me/claims/overdue`），由機構聯繫後自行決定 |
| `AUTO` | 自動釋回，願望重新上架 |

判斷依據是**認領當下的政策快照**，不是機構現在的設定 —— 已經在準備禮物的人不該
因為機構臨時改設定而被無預警收回。

Cloud Run 是 scale-to-zero 的，沒有常駐行程，`@Scheduled` 不會可靠觸發。改由
**Cloud Scheduler 每日呼叫** `POST /internal/jobs/release-expired-claims`，順帶把
實例喚醒。該端點走獨立的安全鏈，只接受指定服務帳號的 Google OIDC token。

管理員也可隨時手動觸發：`POST /api/admin/jobs/release-expired-claims`。

## 正式環境設定

| 環境變數 | 說明 |
|---|---|
| `FIREBASE_PROJECT_ID` | Firebase 專案 ID，用於推導 ID token 的 issuer 與 audience |
| `APP_ADMIN_EMAILS` | 管理員白名單，逗號分隔 |
| `APP_ALLOWED_ORIGINS` | 允許跨來源請求的前端網域 |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | Neon PostgreSQL 連線資訊 |
| `GCS_PUBLIC_BUCKET` | 公開 bucket 名稱（禮物示意圖） |
| `GCS_PRIVATE_BUCKET` | 私密 bucket 名稱（寄送證明、回饋照片） |
| `INTERNAL_JOB_AUDIENCE` | Cloud Scheduler OIDC token 的 audience，設為本服務網址 |
| `SCHEDULER_SERVICE_ACCOUNT` | 允許觸發排程的服務帳號 email |

> **Cloud Run 上簽章的必要設定**：執行環境的服務帳號沒有私鑰檔，簽章會改走 IAM
> `signBlob` API，因此該服務帳號必須擁有**自己**的
> `roles/iam.serviceAccountTokenCreator`。少了這個權限，程式在本機（有金鑰檔）
> 正常，一上 Cloud Run 就會失敗。
>
> 私密 bucket 還需設定 CORS，允許前端網域的 `PUT`，否則瀏覽器直傳會被擋。

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
