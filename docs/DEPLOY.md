# 部署手冊

程式碼側（Dockerfile、GitHub Actions）已經寫好並在本機驗證過。這份文件是**雲端資源
的手動前置步驟**——這些事沒辦法寫進 repo，需要你在各家控制台完成一次。

依序做完之後，往 `main` 推一個 commit 就會自動部署。

> 資料庫誤刪、壞 migration、GCS 物件誤刪這幾種事故的還原程序不在這份文件裡，
> 見 [BACKUP.md](./BACKUP.md)。

---

## 你會用到的三個服務

| 服務 | 用途 | 費用 |
|---|---|---|
| **Firebase** | 使用者登入 + 前端網站託管 | 免費 |
| **Google Cloud** | 後端執行（Cloud Run）、圖片儲存、排程 | 活動期間約 $0–5 |
| **Neon** | PostgreSQL 資料庫 | 免費方案足夠 |

Firebase 專案與 GCP 專案其實是**同一個專案**的兩個面向——在 Firebase 建立專案時
會一併建立對應的 GCP 專案。先建 Firebase，後面 GCP 的操作都在同一個專案裡。

---

## 一、Firebase

### 1.1 建立專案

<https://console.firebase.google.com> → 新增專案。專案 ID 記下來，後面到處會用到
（以下用 `<PROJECT_ID>` 代表）。

> **專案 ID 一旦建立就永久保留，把專案刪掉也拿不回來。** 想在另一個帳號重建就必須
> 換一個新的 ID。Firebase 還常在你輸入的名稱後面自動加上亂數後綴（例如
> `onlinesanta2026-7d653`）——以實際產生的那一個為準，它會滲透到正式網址
> `https://<PROJECT_ID>.web.app`、bucket 名稱、CORS 設定與服務帳號位址。
>
> 後綴不好看也不必為它重建專案：Firebase Hosting 可以在同一個專案下另外加一個
> site，換一個乾淨的 `xxx.web.app` 網址，這件事隨時能做。

### 1.2 開啟登入方式

Authentication → Sign-in method → 啟用這兩項：

- **Google**
- **電子郵件/密碼**（Email/Password）

> 為什麼兩種都開：Google 帳號不限 Gmail（任何信箱都能註冊），但那仍是一道門檻。
> 機構的公務信箱通常沒有對應的 Google 帳號，只給 Google 登入會擋掉一部分人。

### 1.3 確認「每個電子郵件地址一個帳戶」是開啟的

Authentication → Settings → User account linking → **Link accounts that use the
same email**（預設就是開啟）。

> **不要關掉。** 關掉之後同一個信箱會產生多個 Firebase uid，而我們的資料庫有
> `uq_users_email`（一個信箱一筆使用者）——會直接違反唯一索引。開啟時 Firebase 會把
> 同信箱的密碼登入與 Google 登入連結到同一個 uid，我們這邊完全不受影響。

### 1.4 驗證信的範本

Authentication → Templates → 電子郵件地址驗證：

- 語言改成**繁體中文**
- 寄件人名稱改成「線上聖誕老公公」

> 沒有自訂網域之前，寄件人是 `noreply@<專案>.firebaseapp.com`。Gmail 與 Outlook
> 通常收得到，但 hinet 與機構自架的公務信箱可能過濾掉。前端的驗證橫幅已經提示
> 使用者檢查垃圾郵件並提供重寄按鈕；要根治需要自己的網域加上 SPF / DKIM 記錄。

### 1.5 取得網頁設定

專案設定 → 一般 → 你的應用程式 → 新增網頁應用程式。複製這三個值：

- `apiKey`
- `authDomain`
- `projectId`

> 這三個值會出現在瀏覽器裡，**不是秘密**。真正的保護是 Firebase 的安全規則與
> 後端對 ID token 的簽章 / issuer / audience 驗證。

### 1.6 授權網域

Authentication → Settings → Authorized domains，確認 `<PROJECT_ID>.web.app` 在清單裡
（通常會自動加入）。

---

## 二、Neon 資料庫

<https://neon.tech> → 建立專案，區域選離台灣近的（Singapore）。

取得**連線字串**，注意要選 **Pooled connection**：

```
postgresql://user:password@ep-xxx-pooler.ap-southeast-1.aws.neon.tech/neondb?sslmode=require
```

轉成 JDBC 格式，並加上 `prepareThreshold=0`：

```
jdbc:postgresql://ep-xxx-pooler.ap-southeast-1.aws.neon.tech/neondb?sslmode=require&prepareThreshold=0
```

> **為什麼要 `prepareThreshold=0`**：pooled endpoint 走 PgBouncer 的 transaction 模式，
> 同一個連線在不同交易間會被換掉，伺服器端的 prepared statement 因此失效。
> 不關掉會出現間歇性的 `prepared statement "S_1" does not exist`——這種錯誤在
> 低流量時不會出現，正好在上架首日的尖峰爆掉。

使用者名稱與密碼從連線字串裡拆出來，三個值後面要放進 Secret Manager。

---

## 三、Google Cloud

> **這一節的指令是 bash 語法，Windows 請用 Git Bash 或 Cloud Shell 執行，不要用
> PowerShell。** 行尾的 `\` 換行接續、heredoc（`<<'JSON'`）、`for` 迴圈、
> `$(...)` 與 `/tmp` 在 PowerShell 都不成立，逐條翻譯的過程很容易把服務帳號名稱
> 或角色改錯。Cloud Shell（GCP Console 右上角的 `>_`）連 gcloud 都不用裝。

以下指令用 `gcloud`。先安裝並登入：

```bash
gcloud auth login
gcloud config set project <PROJECT_ID>
```

> Git Bash 找不到 `gcloud` 的話，是安裝程式只把路徑加進了 PowerShell。用 `$HOME`
> 補上（不要用 `$LOCALAPPDATA`——它在 Git Bash 裡仍是 `C:\Users\...` 這種反斜線
> 格式，放進 `PATH` 一樣找不到）：
>
> ```bash
> export PATH="$PATH:$HOME/AppData/Local/Google/Cloud SDK/google-cloud-sdk/bin"
> ```

### 3.0 帳單帳戶

**新的 Google 帳號沒有帳單帳戶，下一步會直接失敗：**

```
FAILED_PRECONDITION: Billing account for project '...' is not found.
```

Cloud Run、Artifact Registry、Secret Manager、Cloud Scheduler 都要求專案已連結到有效
的帳單帳戶。**建立帳單帳戶只能在網頁做**，`gcloud` 沒有對應的指令：

<https://console.cloud.google.com/billing> → 建立帳戶 → 國家台灣、幣別 TWD
（**建立後不能改**）→ 綁信用卡（會扣一筆小額驗證後退回）。新帳號可拿到 $300 / 90 天
的試用額度。

過程中會問台灣稅務資訊。沒有統一編號的個人選**未登記稅籍**——Google 開二聯式發票，
5% 營業稅含在你付的金額裡。選「已登記稅籍」卻填不出有效統編會驗證失敗。

建好之後回到終端機把專案綁上去：

```bash
# 取得 XXXXXX-XXXXXX-XXXXXX 格式的 ACCOUNT_ID
gcloud billing accounts list

gcloud billing projects link <PROJECT_ID> --billing-account=<BILLING_ACCOUNT_ID>
```

> **帳單帳戶存在不等於專案綁到它。** `accounts list` 看得到帳戶、`services enable`
> 卻仍然失敗，就是漏了 `projects link` 這一行。這個 ACCOUNT_ID 第七節的預算警示還會
> 再用一次。

### 3.1 啟用 API

```bash
gcloud services enable \
  run.googleapis.com \
  artifactregistry.googleapis.com \
  secretmanager.googleapis.com \
  cloudscheduler.googleapis.com \
  iam.googleapis.com \
  iamcredentials.googleapis.com \
  sts.googleapis.com
```

> **`iam.googleapis.com` 不在新專案的預設啟用清單裡**，而 3.5 的
> `gcloud iam service-accounts create` 與 3.6 的 workload identity pool 都要它。
> 漏掉的話前面幾步都順利，偏偏卡在建立服務帳號。`sts.googleapis.com` 則是 WIF
> 拿 OIDC token 換取短效憑證的那一步會用到。

### 3.2 Artifact Registry

```bash
gcloud artifacts repositories create online-santa \
  --repository-format=docker \
  --location=asia-east1 \
  --description="線上聖誕老公公的容器映像檔"
```

### 3.3 儲存空間

程式碼支援兩個 bucket——公開的放禮物示意圖、私密的放寄送證明與回饋照片。**目前只
需要建私密的那一個**：願望示意圖預設是關閉的（`app.storage.wish-image-enabled`），
願望牆改用分類圖示，公開 bucket 因此不必存在。要開啟見第九節。

```bash
# 私密：寄送證明（含捐贈者姓名地址）與回饋照片（可能含孩童影像）。
# 一律要限時簽章才讀得到，絕不可設為公開
gcloud storage buckets create gs://<PROJECT_ID>-private \
  --location=asia-east1 --uniform-bucket-level-access
```

前端要能直傳，私密 bucket 需要 CORS：

```bash
cat > /tmp/cors.json <<'JSON'
[{
  "origin": ["https://<PROJECT_ID>.web.app", "http://localhost:5173"],
  "method": ["PUT", "GET"],
  "responseHeader": ["Content-Type"],
  "maxAgeSeconds": 3600
}]
JSON
gcloud storage buckets update gs://<PROJECT_ID>-private --cors-file=/tmp/cors.json
```

> **依敏感度分成兩個 bucket 是隱私設計的一部分**，日後啟用示意圖時不要把它們合成
> 一個。示意圖不含孩童影像所以可以公開；寄送證明與回饋照片一旦公開就無法收回。

### 3.4 資料庫連線資訊放 Secret Manager

```bash
printf '%s' 'jdbc:postgresql://ep-xxx-pooler...' | \
  gcloud secrets create online-santa-db-url --data-file=-
printf '%s' '你的使用者名稱' | \
  gcloud secrets create online-santa-db-username --data-file=-
printf '%s' '你的密碼' | \
  gcloud secrets create online-santa-db-password --data-file=-
```

### 3.5 Cloud Run 的執行身分

```bash
gcloud iam service-accounts create online-santa-run \
  --display-name="線上聖誕老公公 Cloud Run"

RUN_SA="online-santa-run@<PROJECT_ID>.iam.gserviceaccount.com"

# 讀 Secret Manager
gcloud projects add-iam-policy-binding <PROJECT_ID> \
  --member="serviceAccount:$RUN_SA" --role=roles/secretmanager.secretAccessor

# 讀寫私密 bucket（公開 bucket 目前不存在，見 3.3）
gcloud storage buckets add-iam-policy-binding gs://<PROJECT_ID>-private \
  --member="serviceAccount:$RUN_SA" --role=roles/storage.objectAdmin
```

**接下來這一步最容易漏，漏了會在本機正常、一上雲就壞：**

```bash
# Cloud Run 的服務帳號沒有私鑰檔，無法在本地簽 URL，函式庫會改呼叫 IAM 的
# signBlob API。這需要它擁有「自己」的 serviceAccountTokenCreator 權限。
gcloud iam service-accounts add-iam-policy-binding "$RUN_SA" \
  --member="serviceAccount:$RUN_SA" \
  --role=roles/iam.serviceAccountTokenCreator
```

### 3.6 GitHub Actions 的部署身分（Workload Identity Federation）

不使用 JSON 金鑰——那是一個放在 GitHub 上、永不過期的憑證。WIF 讓 GitHub 用
短效的 OIDC token 換取權限。

```bash
PROJECT_NUMBER=$(gcloud projects describe <PROJECT_ID> --format='value(projectNumber)')

gcloud iam workload-identity-pools create github \
  --location=global --display-name="GitHub Actions"

gcloud iam workload-identity-pools providers create-oidc github \
  --location=global --workload-identity-pool=github \
  --display-name="GitHub" \
  --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository" \
  --attribute-condition="assertion.repository == 'justinwu1220/online-santa'" \
  --issuer-uri="https://token.actions.githubusercontent.com"

gcloud iam service-accounts create github-deployer \
  --display-name="GitHub Actions 部署"

DEPLOY_SA="github-deployer@<PROJECT_ID>.iam.gserviceaccount.com"

for role in roles/run.admin roles/artifactregistry.writer roles/firebasehosting.admin; do
  gcloud projects add-iam-policy-binding <PROJECT_ID> \
    --member="serviceAccount:$DEPLOY_SA" --role=$role
done

# 部署身分要能「扮演」Cloud Run 的執行身分才能部署服務
gcloud iam service-accounts add-iam-policy-binding "$RUN_SA" \
  --member="serviceAccount:$DEPLOY_SA" --role=roles/iam.serviceAccountUser

# 只有這個 repo 的 workflow 可以取得這個身分
gcloud iam service-accounts add-iam-policy-binding "$DEPLOY_SA" \
  --member="principalSet://iam.googleapis.com/projects/$PROJECT_NUMBER/locations/global/workloadIdentityPools/github/attribute.repository/justinwu1220/online-santa" \
  --role=roles/iam.workloadIdentityUser
```

> `attribute-condition` 那一行是必要的。少了它，**任何** GitHub repo 的 workflow
> 都能拿到這個身分。

---

## 四、GitHub 的設定值

到 repo 的 Settings → Secrets and variables → Actions。

### Secrets（機密）

| 名稱 | 值 |
|---|---|
| `WIF_PROVIDER` | `projects/<PROJECT_NUMBER>/locations/global/workloadIdentityPools/github/providers/github` |
| `WIF_SERVICE_ACCOUNT` | `github-deployer@<PROJECT_ID>.iam.gserviceaccount.com` |
| `MAIL_PASSWORD` | SMTP 服務的密碼／API key，見下方「Email 通知」 |

### Variables（非機密，會出現在建置紀錄裡）

| 名稱 | 值 |
|---|---|
| `GCP_PROJECT_ID` | `<PROJECT_ID>` |
| `FIREBASE_PROJECT_ID` | `<PROJECT_ID>` |
| `GCS_PUBLIC_BUCKET` | 先不用設——啟用示意圖時才需要，見第九節 |
| `GCS_PRIVATE_BUCKET` | `<PROJECT_ID>-private` |
| `APP_ADMIN_EMAILS` | **不要設**——見第五節之後的「管理員的升降級」。首次部署想省事的話可以填你的信箱，登入一次取得管理員身分後再刪掉 |
| `APP_ALLOWED_ORIGINS` | `https://<PROJECT_ID>.web.app` |
| `VITE_FIREBASE_API_KEY` | 1.5 取得的 apiKey |
| `VITE_FIREBASE_AUTH_DOMAIN` | 1.5 取得的 authDomain |
| `INTERNAL_JOB_AUDIENCE` | `https://online-santa/internal-jobs`（見下方說明） |
| `SCHEDULER_SERVICE_ACCOUNT` | `online-santa-scheduler@<PROJECT_ID>.iam.gserviceaccount.com` |
| `MAIL_HOST` | SMTP 主機，見下方「Email 通知」 |
| `MAIL_PORT` | SMTP 連接埠，通常是 `587` |
| `MAIL_USERNAME` | SMTP 帳號 |
| `MAIL_FROM` | 通知信的寄件人地址 |
| `APP_PUBLIC_URL` | `https://<PROJECT_ID>.web.app`——通知信內文的連結會指向這裡 |

> **`INTERNAL_JOB_AUDIENCE` 為什麼是一個看起來假的網址**：OIDC token 的 audience
> 可以是任意字串，只要排程與後端兩邊一致即可。刻意不用 Cloud Run 的網址，
> 因為那要等第一次部署後才知道——會變成先有雞還是先有蛋。

### Email 通知

`MAIL_HOST/PORT/USERNAME/PASSWORD/FROM` 不綁定特定供應商——任何符合 SMTP 的服務
都能填（Gmail 的應用程式密碼、SendGrid、Mailgun 等）。**這五個變數全部留空是合法
狀態**：後端偵測到 `MAIL_HOST` 是空的就自動降級成 no-op（只記 log，不寄信），不會
因為沒設定就啟動失敗，因此可以先不申請 SMTP 服務就上線，通知功能之後再補設定、
重新部署即可生效，不必改程式碼。

`APP_PUBLIC_URL` 一律要設（即使暫時不用 email）：通知信內文的連結（例如「查看認領
詳情」）是拿這個值組出來的，不是後端 API 自己的網址——使用者到不了 API 的網址。

---

## 五、第一次部署

```bash
git commit --allow-empty -m "chore: 觸發首次部署"
git push
```

到 Actions 看三個 job：`verify` → `backend` → `frontend`。

完成後：
- API：`https://online-santa-api-xxxxx-de.a.run.app`（Actions 記錄裡有）
- 前端：`https://<PROJECT_ID>.web.app`

---

## 五之二、管理員的升降級

**管理員身分存在資料庫的 `users.role`，不在設定檔裡。** 授權鏈是
`requireAdmin()` → `isAdmin()` → `effectiveRole()`，讀的一路都是資料庫；
`APP_ADMIN_EMAILS` 完全不參與授權。

那白名單是做什麼的？解「開機」問題：migration 不會塞任何使用者，`users` 表初始
是空的，使用者是第一次登入時才建立。所以剛部署完的系統裡一個管理員都沒有，而要
透過監控中心指派管理員又得先有一個管理員——雞生蛋。白名單讓第一個管理員能靠設定
產生，不必對正式資料庫下 SQL。

**取得第一個管理員之後就把變數刪掉**，之後一律用資料庫管理。留著沒有壞處，但也沒
有用處，反而多一個會與資料庫不一致的來源。

### 日常操作

到 Neon Console → SQL Editor。**一律先 `SELECT` 看清楚會動到哪幾筆再 `UPDATE`。**

```sql
-- 目前有哪些管理員
SELECT email, role, organization_id, last_login_at FROM users WHERE role = 'ADMIN';

-- 提升（那個人必須先登入過一次，資料庫裡才有這筆資料）
UPDATE users SET role = 'ADMIN', updated_at = now()
WHERE email = '要提升的信箱' AND role = 'DONOR' AND organization_id IS NULL;

-- 降級
UPDATE users SET role = 'DONOR', updated_at = now()
WHERE email = '要降級的信箱' AND role = 'ADMIN';
```

每個請求都會重讀資料庫，所以**改完下一個請求就生效**，不必登出或等 token 過期。

> `organization_id IS NULL` 那個條件不是裝飾：`ck_users_org_membership` 要求
> 非 ORG_MEMBER 的人不能隸屬機構，而且機構成員兼任管理員等於球員兼裁判。

### 救援：一個管理員都不剩時

帳號被刪、被誤降級、或換了一個全新的資料庫，都會落到這個狀態。兩條路：

1. 直接在 Neon 跑上面的 `UPDATE`（最快）
2. 設回 `APP_ADMIN_EMAILS` 並重新部署，該信箱**下次登入**時自動取得 ADMIN

第二條的前提是信箱已驗證——用 Google 登入天生滿足，密碼註冊要先點驗證信。

### 已知的不對稱

**把信箱從白名單拿掉不會降級任何人。** 提升是單向的，這是刻意的設計（`AppPrincipal`
的註解有寫），但很容易誤會。撤銷管理員一律走資料庫，不要以為改設定就夠了。

---

## 六、排程（第一次部署後才能設定）

需要 Cloud Run 的實際網址，所以放在最後。

```bash
gcloud iam service-accounts create online-santa-scheduler \
  --display-name="逾期認領掃描"

SCHED_SA="online-santa-scheduler@<PROJECT_ID>.iam.gserviceaccount.com"
API_URL=$(gcloud run services describe online-santa-api \
  --region=asia-east1 --format='value(status.url)')

gcloud scheduler jobs create http release-expired-claims \
  --location=asia-east1 \
  --schedule="0 3 * * *" \
  --time-zone="Asia/Taipei" \
  --uri="$API_URL/internal/jobs/release-expired-claims" \
  --http-method=POST \
  --oidc-service-account-email="$SCHED_SA" \
  --oidc-token-audience="https://online-santa/internal-jobs"

gcloud scheduler jobs create http cleanup-pending-attachments \
  --location=asia-east1 \
  --schedule="0 4 * * *" \
  --time-zone="Asia/Taipei" \
  --uri="$API_URL/internal/jobs/cleanup-pending-attachments" \
  --http-method=POST \
  --oidc-service-account-email="$SCHED_SA" \
  --oidc-token-audience="https://online-santa/internal-jobs"

gcloud scheduler jobs create http send-deadline-reminders \
  --location=asia-east1 \
  --schedule="0 9 * * *" \
  --time-zone="Asia/Taipei" \
  --uri="$API_URL/internal/jobs/send-deadline-reminders" \
  --http-method=POST \
  --oidc-service-account-email="$SCHED_SA" \
  --oidc-token-audience="https://online-santa/internal-jobs"
```

> `--oidc-token-audience` 必須與 `INTERNAL_JOB_AUDIENCE` 完全一致，否則後端會
> 拒絕（這正是設計上要擋掉「任何 Google 帳號都能打進來」的那道檢查）。兩個排程
> 共用同一個服務帳號與 audience，`InternalJobSecurityConfig` 的驗證鏈是依路徑
> 前綴 `/internal/**` 套用的，不是逐一端點設定。

驗證：

```bash
gcloud scheduler jobs run release-expired-claims --location=asia-east1
gcloud scheduler jobs run cleanup-pending-attachments --location=asia-east1
gcloud scheduler jobs run send-deadline-reminders --location=asia-east1
```

也可以登入監控中心，在「系統與稽核」按「立即執行」——同一段邏輯，不同的身分驗證。
附件清理建議排在逾期釋回之後（凌晨 4 點 vs 3 點）：兩者互不相依，錯開只是避免
同時打進資料庫的尖峰重疊。寄送期限提醒刻意排在早上 9 點而非半夜——前兩個是內部
維運排程，這個是真的會寄到捐贈者信箱的信，半夜寄出沒有意義，還可能被當垃圾信。

---

## 七、成本護欄

```bash
# 帳單警示。設定後超過門檻會寄信，但不會自動停用服務
gcloud billing budgets create \
  --billing-account=<BILLING_ACCOUNT_ID> \
  --display-name="線上聖誕老公公" \
  --budget-amount=10USD \
  --threshold-rule=percent=50 \
  --threshold-rule=percent=90 \
  --threshold-rule=percent=100
```

`<BILLING_ACCOUNT_ID>` 就是 3.0 建立的那一個，忘記的話 `gcloud billing accounts list`
可以查。

Cloud Run 的 `--max-instances=10` 已經寫在 workflow 裡，這是更硬的護欄：
就算被爬蟲或攻擊打，也最多只會有 10 個實例。

---

## 八、上架首日的建議

活動開始前幾天，把最小實例數調成 1：

```bash
gcloud run services update online-santa-api --region=asia-east1 --min-instances=1
```

冷啟動已經靠 CDS 壓到 5 秒左右，但搶領尖峰時第一個使用者仍會等到。
常駐幾天的費用微不足道（約 $1-2），體驗差別很大。

活動結束後記得改回來：

```bash
gcloud run services update online-santa-api --region=asia-east1 --min-instances=0
```

---

## 九、日後啟用願望示意圖

願望牆目前用分類圖示，機構不能上傳禮物示意圖。這是成本決定：示意圖放在公開 bucket、
在願望牆大量曝光，是整個系統唯一會隨訪客數線性成長的費用來源。關閉時公開 bucket
根本不存在，那筆錢就是零。

程式碼完整保留著，沒有刪任何東西——啟用不需要改程式、不需要 migration、不需要回填
資料。既有的願望沒有圖片會繼續顯示分類圖示，新舊混排是合法狀態。

### 9.1 建立公開 bucket

```bash
# 只放禮物示意圖。不含孩童影像，公開沒有隱私風險，
# 而且願望牆流量最大，公開網址省下每張圖的簽章往返
gcloud storage buckets create gs://<PROJECT_ID>-public \
  --location=asia-east1 --uniform-bucket-level-access
gcloud storage buckets add-iam-policy-binding gs://<PROJECT_ID>-public \
  --member=allUsers --role=roles/storage.objectViewer

# 前端直傳需要 CORS（內容與 3.3 的那份相同）
gcloud storage buckets update gs://<PROJECT_ID>-public --cors-file=/tmp/cors.json

# Cloud Run 的執行身分要能讀寫它
gcloud storage buckets add-iam-policy-binding gs://<PROJECT_ID>-public \
  --member="serviceAccount:online-santa-run@<PROJECT_ID>.iam.gserviceaccount.com" \
  --role=roles/storage.objectAdmin
```

### 9.2 設定兩個 Variable

Settings → Secrets and variables → Actions → Variables：

| 名稱 | 值 |
|---|---|
| `GCS_PUBLIC_BUCKET` | `<PROJECT_ID>-public` |
| `WISH_IMAGE_ENABLED` | `true` |

`WISH_IMAGE_ENABLED` 同時餵給後端（`app.storage.wish-image-enabled`，決定端點收不收
上傳）與前端（`VITE_WISH_IMAGE_ENABLED`，決定畫不畫上傳按鈕）。**兩邊必須一致**——
只開前端會讓機構按下去拿到 403。

### 9.3 重新部署

到 Actions 手動觸發 `Deploy`（`workflow_dispatch`）。前端是建置時把旗標編進去的，
所以一定要重跑一次建置，只重啟 Cloud Run 沒有用。

### 9.4 開啟前先確認成本

啟用後的流量費用取決於圖片大小，不是訪客數。以每張 2.5 MB 的手機原圖、每位訪客
瀏覽 25 張估算，3,000 位訪客約 188 GB 出網、約 NT$720；壓縮到 150 KB 則約 NT$43。

**啟用示意圖之前，先讓 `frontend/src/lib/upload.ts` 在上傳前壓縮圖片**（長邊縮到
1200px 並轉 WebP）。`MAX_IMAGE_BYTES` 只擋 5 MB 上限，不會縮小已經合規的檔案。
同時把第七節的預算警示調高。

---

## 疑難排解

| 症狀 | 原因 |
|---|---|
| `services enable` 說 billing account is not found | 帳單帳戶沒建，或建了卻漏掉 `gcloud billing projects link`，見 3.0 |
| `iam service-accounts create` 說 API 未啟用 | `iam.googleapis.com` 沒開，見 3.1 |
| 部署時 `iam.serviceaccounts.actAs` denied on `...-compute@developer` | workflow 的 `flags` 少了 `--service-account`，Cloud Run 退回用 Compute Engine 預設服務帳號，而部署身分沒有它的 actAs 權限 |
| 上傳圖片時 500，本機卻正常 | 漏了 3.5 最後那個 `serviceAccountTokenCreator` |
| 瀏覽器上傳被 CORS 擋下 | bucket 的 CORS 沒設，或 origin 沒包含實際網域 |
| 間歇性 `prepared statement does not exist` | JDBC 連線字串少了 `prepareThreshold=0` |
| 排程回 401 | audience 或服務帳號 email 與後端設定不一致 |
| 啟動時 Flyway 失敗 | Neon 專案在冷啟動，重試即可；或連線字串有誤 |
| 登入後一直是 DONOR | 資料庫裡那筆使用者的 `role` 不是 ADMIN。用白名單救援時記得信箱要完全相符，改完要重新部署 |
| 把信箱從 `APP_ADMIN_EMAILS` 拿掉，那個人還是管理員 | 提升是單向的，白名單不會降級。要改資料庫，見「管理員的升降級」 |
| 明明是管理員卻被擋在監控中心外 | 信箱未驗證。未驗證的 token 只有一般民眾的權限，去收驗證信 |
| 收不到驗證信 | 檢查垃圾郵件匣；機構的公務信箱過濾較嚴，可先用 Google 登入 |
| 註冊時說信箱已被使用 | 那個信箱先前用另一種方式註冊過，改用原本的方式登入 |
| Cloud Run 部署失敗，說讀不到 secret | 服務沒有以 `online-santa-run` 身分執行。workflow 的 `--service-account` 與 3.5 建立的名稱要一致 |
| 前端 job 失敗，firebase-tools 說找不到專案 | 部署身分除了 `firebasehosting.admin` 可能還需要 `roles/firebase.viewer`，補上再重跑 |
