# 備份與還原手冊

> 對應體檢報告 Tier 1 缺口 #4：`DEPLOY.md` 全文搜「備份」原本零命中。這份文件補的是
> 「壞事發生後怎麼辦」，不是備份策略的理論介紹——每一段都是能直接貼進終端機執行的
> 指令。跟 [DEPLOY.md](./DEPLOY.md) 一樣，指令是 bash 語法，Windows 請用 Git Bash 或
> Cloud Shell。

系統有兩份需要還原能力的資料：Neon 上的 Postgres（結構化資料）與 GCS 上的私密
bucket（寄送證明、回饋照片）。兩者的還原機制完全不同，分開處理。

---

## 一、Neon 資料庫

### 1.1 還原能力從哪裡來

Neon 的儲存引擎本身是 log-structured 的，寫入會保留一段時間的歷史（Neon 稱為
**history retention** 或 **time travel**），讓你能在保留窗口內的任何一個時間點
建立一個新的 **branch**——這個 branch 是那個時間點的完整資料庫快照，可讀寫、
獨立於主 branch，不會動到正式環境。

> **保留窗口的長度依方案而定**（Free 與付費方案不同），實際天數以
> [Neon 官方文件的 history retention 說明](https://neon.tech/docs/introduction/branch-restore)
> 為準——不要憑印象假設是幾天，方案與定價會變動。上線前務必先查一次目前的專案在用
> 哪個方案、對應的保留窗口是多長，寫進團隊內部的值班手冊。

還原的核心動作是「建 branch → 在 branch 上驗證 → 決定怎麼把資料弄回主 branch」，
而不是「直接把主 branch 砍掉重建」。這個順序是刻意的：先在隔離的 branch 上確認
資料真的是你要的那個時間點，再動手，比先動手再發現時間點抓錯安全得多。

### 1.2 情境一：誤刪資料表

假設有人不小心對正式資料庫下了 `DROP TABLE wishes` 或誤刪了一大批列。

```bash
# 1. 在 Neon Console → 專案 → Branches → Create branch，
#    來源選 "Point in time"，選在誤刪動作之前的時間戳（建議抓早 1-2 分鐘的緩衝）。
#    也可以用 CLI：
neonctl branches create --project-id <PROJECT_ID> \
  --name recovery-$(date +%Y%m%d-%H%M) \
  --parent main \
  --timestamp "2026-08-31T10:00:00Z"

# 2. 取得這個新 branch 的連線字串
neonctl connection-string recovery-20260831-1000 --project-id <PROJECT_ID>

# 3. 先在 branch 上確認資料確實還在、確實是你要的那個時間點
psql "postgresql://...recovery-branch.../neondb?sslmode=require" \
  -c "SELECT count(*) FROM wishes;"

# 4. 只把被誤刪的表（或其中被刪的列）匯出，不要整個資料庫覆蓋——
#    正式環境在誤刪之後可能已經有新的合法寫入（新註冊的機構、新認領），
#    整庫回滾會把這些也一起弄丟
pg_dump "postgresql://...recovery-branch.../neondb?sslmode=require" \
  --table=wishes --data-only --format=custom --file=wishes-recovered.dump

# 5. 匯回正式環境（先確認 wishes 表結構還在，只是資料被刪的情境；
#    如果連表結構都被刪了，見下方「連表結構都沒了」）
pg_restore --data-only --disable-triggers \
  --dbname="$PROD_DATABASE_URL" wishes-recovered.dump
```

> **`--disable-triggers`**：`wishes` 有 `updated_at` 的觸發器與外鍵約束，直接
> `INSERT` 回去會因為時間戳或約束檢查順序出錯。這個旗標讓 `pg_restore` 用
> 資料庫超級使用者權限暫時關閉觸發器，只在資料匯入期間生效。

**如果連表結構都被刪了**（`DROP TABLE` 而不是 `DELETE`）：改用 `pg_dump` 不加
`--data-only`，連 schema 一起從 recovery branch 匯出，`pg_restore` 到正式環境
即可重建表結構與資料。

### 1.3 情境二：壞的 migration

Flyway 的 migration 是唯一事實來源，**預設處理方式是往前修，不是往回滾**：

1. 寫一個新的 `V{next}__fix_xxx.sql` 修正問題（例如加回被誤刪的欄位、修正錯的
   資料轉換），照正常流程部署。這個做法的好處是修正本身也進了版控，任何環境
   （包含之後重建的測試環境）套用完整的 migration 順序都會得到正確結果。
2. **只有在 migration 造成了無法用「再寫一個 migration」挽回的資料損失時**
   （例如某支 migration 把一整欄的資料轉型轉錯、原始值已經沒有另一份保留），
   才動用 Neon 的時間點還原：
   ```bash
   # 建一個時間點在「壞 migration 執行之前」的 branch
   neonctl branches create --project-id <PROJECT_ID> \
     --name pre-bad-migration \
     --parent main \
     --timestamp "2026-08-31T09:55:00Z"

   # 從這個 branch 把受影響的欄位/表資料撈出來，比照 1.2 的做法用 pg_dump
   # 匯出、pg_restore 匯回正式環境的對應表——只修資料，不要動 schema，
   # 因為正式環境的 schema 已經是（除了那個轉型錯誤之外）你要的最新版本
   ```
3. 資料修回來之後，**檢查 `flyway_schema_history` 表**：壞的 migration 版本號
   應該仍然標記為已套用（`success = true`），不要手動刪除或竄改這張表的紀錄——
   Flyway 靠它判斷下次部署要不要重新執行這支 migration。真正的修正是第 1 步
   那支新的 migration，`flyway_schema_history` 保持誠實反映「哪些 migration
   真的跑過」。

### 1.4 還原前：先建 branch 驗證

不管哪種情境，**永遠先在獨立 branch 上驗證，再動正式環境**。這條原則值得再說
一次，因為壓力下最容易被跳過的就是這一步：

```bash
# 驗證 checklist（在 branch 的連線字串上跑，不是正式環境）
psql "$BRANCH_URL" -c "SELECT count(*) FROM wishes;"
psql "$BRANCH_URL" -c "SELECT count(*) FROM claims;"
psql "$BRANCH_URL" -c "SELECT max(created_at) FROM claims;"  -- 確認時間點抓對了
```

驗證完、確定這是你要的資料之後，才進行 1.2／1.3 的匯出匯入步驟。事後記得刪掉
用完的 recovery branch（`neonctl branches delete <branch-name>`），避免佔用配額。

---

## 二、GCS 私密 bucket

私密 bucket（`<PROJECT_ID>-private`）放的是寄送證明與回饋照片——回饋照片可能含
孩童影像，是全系統敏感度最高的檔案，被誤刪或誤覆寫的代價最高。

### 2.1 開啟 bucket versioning

**這是還原能力的前提**，沒有這一步，物件一旦被刪除或覆寫就真的救不回來：

```bash
gcloud storage buckets update gs://<PROJECT_ID>-private --versioning
```

確認已經開啟：

```bash
gcloud storage buckets describe gs://<PROJECT_ID>-private --format="value(versioning.enabled)"
# 應該回 True
```

> Versioning 開啟後，刪除或覆寫物件時，舊版本不會真的消失，只是不再是「目前版本」
> （current version），會多算一份儲存空間直到被 lifecycle 規則清掉（見 2.3）。
> 這是刻意的取捨：多付一點儲存費，換回饋照片誤刪不會真的萬劫不復。

### 2.2 還原誤刪或誤覆寫的物件

```bash
# 1. 列出這個物件的所有版本（含已經不是 current 的舊版本），
#    -a 會列出所有版本，每個版本有自己的 generation 號碼
gcloud storage ls -a gs://<PROJECT_ID>-private/feedback/<claim-id>/<file>.jpg

# 輸出範例，#後面那串數字就是 generation：
#   gs://xxx-private/feedback/.../abc.jpg#1735689600123456
#   gs://xxx-private/feedback/.../abc.jpg#1735776000654321  (這個可能是誤刪前的版本)

# 2. 確認要還原的是哪一個版本（用時間戳比對事故發生時間）
gcloud storage objects describe \
  "gs://<PROJECT_ID>-private/feedback/<claim-id>/<file>.jpg#1735776000654321"

# 3. 把那個版本複製回去、變成目前版本——這一步不會刪掉中間任何版本，
#    是新增一次複製動作，最安全
gcloud storage cp \
  "gs://<PROJECT_ID>-private/feedback/<claim-id>/<file>.jpg#1735776000654321" \
  "gs://<PROJECT_ID>-private/feedback/<claim-id>/<file>.jpg"
```

**如果不知道確切的物件路徑**（例如只知道是某筆認領、不確定是哪個檔名）：
先查 `attachments` 表的 `object_name` 欄位再對應——這也是為什麼刪除附件功能
（見 `AttachmentService.delete`）目前是先刪 GCS 物件才刪 DB 列：DB 列消失前，
`object_name` 都還查得到，方便對照 GCS 那邊該找哪個物件。

### 2.3 Lifecycle 規則：非目前版本保留 30 天

Versioning 開啟後，舊版本會一直累積、一直算錢，除非設 lifecycle 規則自動清掉：

```bash
cat > /tmp/lifecycle.json <<'JSON'
{
  "rule": [
    {
      "action": {"type": "Delete"},
      "condition": {
        "isLive": false,
        "daysSinceNoncurrentTime": 30
      }
    }
  ]
}
JSON

gcloud storage buckets update gs://<PROJECT_ID>-private --lifecycle-file=/tmp/lifecycle.json
```

確認規則已生效：

```bash
gcloud storage buckets describe gs://<PROJECT_ID>-private --format="default(lifecycle_config)"
```

> `isLive: false` 只鎖定「非目前版本」（也就是被刪除/覆寫後留下的舊版本），
> 目前版本永遠不會被這條規則清掉——它不是一般的到期刪除，是「還原窗口只給
> 30 天」的意思。30 天內誤刪都救得回來，超過就真的沒了。這個天數與 GCS
> lifecycle 的最小粒度綁定（以天為單位），30 天是與 Neon 保留窗口類似量級
> 的合理起點，之後可依實際事故頻率與儲存成本調整。

---

## 三、演練檢查清單（建議每季一次）

備份能力不演練就等於沒有——沒人知道指令是不是真的能用，直到需要用的那一刻才
發現連線字串格式錯了或權限不夠。

- [ ] **Neon**：建一個 time-travel branch（隨便選一個一週前的時間點），連線
      確認資料存在，然後刪掉這個 branch（`neonctl branches delete`）。
      目的：確認 `neonctl` 或 Console 操作流程還熟悉、時間點語法沒記錯。
- [ ] **GCS**：找一個私密 bucket 裡的既有物件，故意 `cp` 覆寫成別的內容製造
      一個新版本，用 2.2 的步驟把原本的版本還原回來，確認內容位元組對位元組
      一致（`gcloud storage cp` 後 diff 或比對 checksum）。
      目的：確認 versioning 真的有開、generation 語法沒記錯。
- [ ] 確認負責演練的人**不是**當初設定備份機制的那個人——由不熟悉細節的人
      跟著這份文件操作一次，才測得出文件本身寫得夠不夠清楚。
- [ ] 演練完在團隊內部記錄一行「哪天、誰、結果如何」，發現文件哪裡不準確
      就當場改掉這份文件，不要留到下次事故才發現指令是錯的。

---

## 四、還沒做的事（誠實列出，不要假裝這份文件解決了全部）

- 目前沒有**自動化**的定期備份匯出（例如每日 `pg_dump` 存到另一個地方）。
  Neon 的 time-travel 只在保留窗口內有效，超過窗口的資料無法還原。若需要
  更長的保留期，要另外排程 `pg_dump` 並存到獨立於 Neon 帳號的地方（例如
  另一個雲端帳號的 GCS bucket），避免「Neon 帳號本身出事」這個單點故障。
- 目前沒有跨區域備援。Neon 與 GCS bucket 目前都只在單一區域
  （`asia-east1`／Neon 的 Singapore 區域），區域級事故（機率低但非零）
  不在這份手冊涵蓋範圍。
- 這份手冊沒有涵蓋 Firebase Auth 的使用者資料還原——Firebase 本身的備份/
  匯出能力另外查 Firebase 官方文件，不在這次體檢報告的範圍內。
