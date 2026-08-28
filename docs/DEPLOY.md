# 部署手冊

程式碼側（Dockerfile、GitHub Actions）已經寫好並在本機驗證過。這份文件是**雲端資源
的手動前置步驟**——這些事沒辦法寫進 repo，需要你在各家控制台完成一次。

依序做完之後，往 `main` 推一個 commit 就會自動部署。

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

以下指令用 `gcloud`。先安裝並登入：

```bash
gcloud auth login
gcloud config set project <PROJECT_ID>
```

### 3.1 啟用 API

```bash
gcloud services enable \
  run.googleapis.com \
  artifactregistry.googleapis.com \
  secretmanager.googleapis.com \
  cloudscheduler.googleapis.com \
  iamcredentials.googleapis.com
```

### 3.2 Artifact Registry

```bash
gcloud artifacts repositories create online-santa \
  --repository-format=docker \
  --location=asia-east1 \
  --description="線上聖誕老公公的容器映像檔"
```

### 3.3 兩個儲存空間

依敏感度分開，**這是隱私設計的一部分**，不要合成一個：

```bash
# 公開：只放禮物示意圖。不含孩童影像，公開沒有隱私風險，
# 而且願望牆流量最大，公開網址省下每張圖的簽章往返
gcloud storage buckets create gs://<PROJECT_ID>-public \
  --location=asia-east1 --uniform-bucket-level-access
gcloud storage buckets add-iam-policy-binding gs://<PROJECT_ID>-public \
  --member=allUsers --role=roles/storage.objectViewer

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
gcloud storage buckets update gs://<PROJECT_ID>-public --cors-file=/tmp/cors.json
```

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

# 讀寫兩個 bucket
for b in public private; do
  gcloud storage buckets add-iam-policy-binding gs://<PROJECT_ID>-$b \
    --member="serviceAccount:$RUN_SA" --role=roles/storage.objectAdmin
done
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

### Variables（非機密，會出現在建置紀錄裡）

| 名稱 | 值 |
|---|---|
| `GCP_PROJECT_ID` | `<PROJECT_ID>` |
| `FIREBASE_PROJECT_ID` | `<PROJECT_ID>` |
| `GCS_PUBLIC_BUCKET` | `<PROJECT_ID>-public` |
| `GCS_PRIVATE_BUCKET` | `<PROJECT_ID>-private` |
| `APP_ADMIN_EMAILS` | 你的管理員信箱，逗號分隔 |
| `APP_ALLOWED_ORIGINS` | `https://<PROJECT_ID>.web.app` |
| `VITE_FIREBASE_API_KEY` | 1.5 取得的 apiKey |
| `VITE_FIREBASE_AUTH_DOMAIN` | 1.5 取得的 authDomain |
| `INTERNAL_JOB_AUDIENCE` | `https://online-santa/internal-jobs`（見下方說明） |
| `SCHEDULER_SERVICE_ACCOUNT` | `online-santa-scheduler@<PROJECT_ID>.iam.gserviceaccount.com` |

> **`INTERNAL_JOB_AUDIENCE` 為什麼是一個看起來假的網址**：OIDC token 的 audience
> 可以是任意字串，只要排程與後端兩邊一致即可。刻意不用 Cloud Run 的網址，
> 因為那要等第一次部署後才知道——會變成先有雞還是先有蛋。

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
```

> `--oidc-token-audience` 必須與 `INTERNAL_JOB_AUDIENCE` 完全一致，否則後端會
> 拒絕（這正是設計上要擋掉「任何 Google 帳號都能打進來」的那道檢查）。

驗證：

```bash
gcloud scheduler jobs run release-expired-claims --location=asia-east1
```

也可以登入監控中心，在「系統與稽核」按「立即執行」——同一段邏輯，不同的身分驗證。

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

## 疑難排解

| 症狀 | 原因 |
|---|---|
| 上傳圖片時 500，本機卻正常 | 漏了 3.5 最後那個 `serviceAccountTokenCreator` |
| 瀏覽器上傳被 CORS 擋下 | bucket 的 CORS 沒設，或 origin 沒包含實際網域 |
| 間歇性 `prepared statement does not exist` | JDBC 連線字串少了 `prepareThreshold=0` |
| 排程回 401 | audience 或服務帳號 email 與後端設定不一致 |
| 啟動時 Flyway 失敗 | Neon 專案在冷啟動，重試即可；或連線字串有誤 |
| 登入後一直是 DONOR | `APP_ADMIN_EMAILS` 沒設或拼錯，改完要重新部署 |
| 明明是管理員卻被擋在監控中心外 | 信箱未驗證。未驗證的 token 只有一般民眾的權限，去收驗證信 |
| 收不到驗證信 | 檢查垃圾郵件匣；機構的公務信箱過濾較嚴，可先用 Google 登入 |
| 註冊時說信箱已被使用 | 那個信箱先前用另一種方式註冊過，改用原本的方式登入 |
