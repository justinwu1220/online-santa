#!/usr/bin/env python3
"""
建立寄信與用量的監控：log-based metrics、通知頻道、告警政策。

為什麼需要：測試站與正式站共用同一個寄信帳號，而註冊、忘記密碼都會寄信。被機器人批次註冊、
或某個環境出錯陷入迴圈，都能在短時間內燒光寄信額度（正式站升級成不限量方案後，更沒有程式內的
每日上限擋著），告警是主要的防線。

用法（需要 gcloud 已登入，且帳號有 monitoring / logging 的管理權限）：
    python ops/monitoring/apply.py --project <PROJECT_ID> --email you@example.com            # 只預覽
    python ops/monitoring/apply.py --project <PROJECT_ID> --email you@example.com --apply    # 真的建立
    python ops/monitoring/apply.py ... --apply --replace    # 同名的告警政策先刪掉再建（要改門檻時）

可重複執行：已存在的 metric、頻道、政策會略過（--replace 只針對告警政策）。

告警掛在這些固定的 log 字串上（定義在 NotificationService，不可隨文案改）：
    MAIL_FAILING_REPEATEDLY   連續多封寄信失敗，寄信功能可能整體失效
    MAIL_DAILY_LIMIT_REACHED  今天寄信已達每日上限，之後的信不再寄
    MAIL_DAILY_LIMIT_NEARING  用掉每日上限的 80%
    通知信已寄出               每寄出一封都會記一行，用來算寄信量
"""
import argparse
import json
import subprocess
import sys
import urllib.error
import urllib.request

PROD = 'online-santa-api'
STAGING = 'online-santa-api-staging'

# 可調整的門檻
PROD_MAILS_PER_HOUR = 1000       # 正式站每小時寄信量；超過就告警（月額度 5 萬封 ≈ 平均每小時 70 封）
STAGING_REQUESTS_PER_5MIN = 300  # 測試站每 5 分鐘請求數；超過代表有東西在狂打（平常幾乎沒有流量）
PROD_5XX_PER_5MIN = 10           # 正式站每 5 分鐘的 5xx 數量

# (名稱, 說明, log 過濾條件)
LOG_METRICS = [
    ('mail_sent', '每寄出一封通知信記一筆（算寄信量用）',
     'resource.type="cloud_run_revision" AND textPayload:"通知信已寄出"'),
    ('mail_failing_repeatedly', '連續多封寄信失敗',
     'resource.type="cloud_run_revision" AND textPayload:"MAIL_FAILING_REPEATEDLY"'),
    ('mail_daily_limit', '寄信達到或接近每日上限',
     'resource.type="cloud_run_revision" AND '
     '(textPayload:"MAIL_DAILY_LIMIT_REACHED" OR textPayload:"MAIL_DAILY_LIMIT_NEARING")'),
]


def threshold_policy(name, filter_, threshold, period_seconds, doc, channel):
    return {
        'displayName': name,
        'combiner': 'OR',
        'conditions': [{
            'displayName': name,
            'conditionThreshold': {
                'filter': filter_,
                'aggregations': [{
                    'alignmentPeriod': f'{period_seconds}s',
                    'perSeriesAligner': 'ALIGN_SUM',
                    'crossSeriesReducer': 'REDUCE_SUM',
                }],
                'comparison': 'COMPARISON_GT',
                'thresholdValue': threshold,
                'duration': '0s',
                'trigger': {'count': 1},
            },
        }],
        'notificationChannels': [channel],
        'alertStrategy': {'autoClose': '3600s'},
        'documentation': {'content': doc, 'mimeType': 'text/markdown'},
    }


def log_metric(metric, service=None):
    clause = f' AND resource.label.service_name="{service}"' if service else ''
    return f'metric.type="logging.googleapis.com/user/{metric}" AND resource.type="cloud_run_revision"{clause}'


def run_metric(metric, extra=''):
    return f'metric.type="run.googleapis.com/{metric}" AND resource.type="cloud_run_revision"{extra}'


def policies(channel):
    return [
        threshold_policy(
            '寄信連續失敗（任一環境）', log_metric('mail_failing_repeatedly'), 0, 300,
            '連續多封通知信寄不出去，寄信功能可能整體失效：寄信額度耗盡、SMTP 帳密失效或供應商異常。\n\n'
            '**影響**：註冊的驗證碼與忘記密碼的信都收不到，使用者無法完成註冊。\n\n'
            '**處理**：到 Cloud Logging 搜尋 `MAIL_FAILING_REPEATEDLY` 看伺服器的回應原文；'
            '登入 Resend 後台確認額度與 API key。失敗的信不會自動重寄。', channel),
        threshold_policy(
            '寄信達每日上限（測試站）', log_metric('mail_daily_limit', STAGING), 0, 300,
            '測試站今天的寄信量達到每日上限（或已用掉 80%）。測試站與正式站共用寄信額度，'
            '代表測試站可能有東西在大量寄信（迴圈、被機器人註冊）。\n\n'
            '**處理**：到 Cloud Logging 看測試站（`online-santa-api-staging`）最近寄了什麼；'
            '必要時 `gcloud run services update online-santa-api-staging --max-instances=0` 暫停測試站。', channel),
        threshold_policy(
            '正式站寄信量異常', log_metric('mail_sent', PROD), PROD_MAILS_PER_HOUR, 3600,
            f'正式站過去一小時寄出超過 {PROD_MAILS_PER_HOUR} 封信。正式站沒有設每日上限，這是主要的防線。\n\n'
            '**處理**：到 Cloud Logging 搜尋「通知信已寄出」看收件人與主旨是否正常；'
            '被機器人批次註冊的話，可以先用 `gcloud run services update online-santa-api '
            '--update-env-vars MAIL_DAILY_LIMIT=<數字>` 臨時加上每日上限（立即生效，不必重新部署映像檔）。\n\n'
            '平常活動期間的寄信量若本來就高，請調整這個門檻（`ops/monitoring/apply.py` 的 PROD_MAILS_PER_HOUR）。',
            channel),
        threshold_policy(
            '測試站請求量異常', run_metric('request_count', f' AND resource.label.service_name="{STAGING}"'),
            STAGING_REQUESTS_PER_5MIN, 300,
            f'測試站 5 分鐘內的請求超過 {STAGING_REQUESTS_PER_5MIN} 次。測試站平常幾乎沒有流量，'
            '可能是失控的迴圈或被掃描。\n\n**處理**：看 Cloud Run 的請求紀錄；必要時暫停測試站。', channel),
        threshold_policy(
            '正式站 5xx 錯誤', run_metric('request_count',
                                           f' AND resource.label.service_name="{PROD}"'
                                           ' AND metric.label.response_code_class="5xx"'),
            PROD_5XX_PER_5MIN, 300,
            f'正式站 5 分鐘內出現超過 {PROD_5XX_PER_5MIN} 個 5xx 回應。\n\n'
            '**處理**：看 Cloud Logging 的 ERROR；剛部署過的話考慮把流量切回前一個 revision。', channel),
    ]


# ---------------------------------------------------------------------------------------------

def token():
    return subprocess.run('gcloud auth print-access-token', shell=True, capture_output=True,
                          text=True, check=True).stdout.strip()


def call(project, method, path, body=None):
    request = urllib.request.Request(
        f'https://monitoring.googleapis.com/v3/{path}', method=method,
        data=None if body is None else json.dumps(body).encode(),
        headers={'Authorization': f'Bearer {token()}', 'X-Goog-User-Project': project,
                 'Content-Type': 'application/json'})
    try:
        with urllib.request.urlopen(request) as response:
            text = response.read().decode()
            return json.loads(text) if text else {}
    except urllib.error.HTTPError as e:
        sys.exit(f'{method} {path} 失敗：{e.code} {e.read().decode()[:400]}')


def main():
    # Windows 終端機預設的編碼不是 UTF-8，中文會變亂碼
    if hasattr(sys.stdout, 'reconfigure'):
        sys.stdout.reconfigure(encoding='utf-8')
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--project', required=True)
    parser.add_argument('--email', required=True, help='收告警的信箱')
    parser.add_argument('--apply', action='store_true', help='真的建立（沒加就只預覽）')
    parser.add_argument('--replace', action='store_true', help='同名的告警政策先刪掉再建')
    args = parser.parse_args()
    project, mode = args.project, ('建立' if args.apply else '[預覽] 將會建立')

    print(f'專案：{project}，告警信箱：{args.email}\n')

    # 1. log-based metrics
    existing = subprocess.run(f'gcloud logging metrics list --project={project} --format="value(name)"',
                              shell=True, capture_output=True, text=True, check=True).stdout.split()
    for name, description, log_filter in LOG_METRICS:
        if name in existing:
            print(f'略過（已存在）  log metric {name}')
            continue
        print(f'{mode}  log metric {name}')
        if args.apply:
            subprocess.run(['gcloud', 'logging', 'metrics', 'create', name, f'--project={project}',
                            f'--description={description}', f'--log-filter={log_filter}'],
                           check=True, shell=(sys.platform == 'win32'))

    # 2. 通知頻道
    channels = call(project, 'GET', f'projects/{project}/notificationChannels').get('notificationChannels', [])
    channel = next((c['name'] for c in channels
                    if c.get('labels', {}).get('email_address') == args.email), None)
    if channel:
        print(f'略過（已存在）  通知頻道 {args.email}')
    else:
        print(f'{mode}  通知頻道 {args.email}')
        if args.apply:
            channel = call(project, 'POST', f'projects/{project}/notificationChannels', {
                'type': 'email', 'displayName': 'Online Santa 告警',
                'labels': {'email_address': args.email}})['name']
    channel = channel or 'projects/PROJECT/notificationChannels/（預覽時尚未建立）'

    # 3. 告警政策
    current = {p['displayName']: p['name'] for p in
               call(project, 'GET', f'projects/{project}/alertPolicies').get('alertPolicies', [])}
    for policy in policies(channel):
        name = policy['displayName']
        if name in current:
            if not args.replace:
                print(f'略過（已存在）  告警政策 {name}')
                continue
            print(f'{mode.replace("建立", "刪除並重建")}  告警政策 {name}')
            if args.apply:
                call(project, 'DELETE', current[name])
        else:
            print(f'{mode}  告警政策 {name}')
        if args.apply:
            call(project, 'POST', f'projects/{project}/alertPolicies', policy)

    print('\n完成。' if args.apply else '\n以上只是預覽，沒有改動任何東西。確認後加上 --apply。')


if __name__ == '__main__':
    main()
