/**
 * 認領者首次登入說明（「開始之前」彈窗）的內容。
 *
 * 由 docs/style/1018 新版/prototype.html 的 ONBOARD.donor 逐字抽出產生，不要手改文字或插圖——
 * 設計稿更新時請重新抽出。插圖是靜態 SVG 字串、說明裡的 <b> 也是我們自己的靜態標記，
 * 沒有任何使用者資料，所以元件用 dangerouslySetInnerHTML 渲染。
 */
export interface OnboardCard {
  /** 頂端的步驟文字，例如「開始之前」「1 / 3」 */
  step: string
  /** 對應 illustrations 的 key */
  illo: string
  title: string
  /** 可含 <b> */
  sub: string
  /** 主要按鈕文字 */
  next: string
}

export const DONOR_ONBOARD_CARDS: OnboardCard[] = [
  {
    "step": "開始之前",
    "illo": "tree",
    "title": "歡迎成為孩子的聖誕老公公",
    "sub": "開始認領之前，有三件事想先告訴你。",
    "next": "開始"
  },
  {
    "step": "1 / 3",
    "illo": "banned",
    "title": "不寄食物、現金，與危害孩子安全的物品",
    "sub": "完整清單在《認領者參與須知》。情節嚴重時，我們會通報警政單位。",
    "next": "下一步"
  },
  {
    "step": "2 / 3",
    "illo": "ship",
    "title": "寄出時，填寫物流資訊及拍照",
    "sub": "寄出的同時，請到網站填入物流單號，並上傳包裹照片或網購截圖，讓機構知道禮物在路上。",
    "next": "下一步"
  },
  {
    "step": "3 / 3",
    "illo": "balance",
    "title": "量力認領，有困難可以釋出",
    "sub": "請先評估自己的時間與預算再認領。中途無法完成時，請盡快在網站上釋出願望，讓其他人接手。",
    "next": "下一步"
  },
  {
    "step": "準備好了",
    "illo": "hat",
    "title": "開始認領孩子的願望吧",
    "sub": "完整規範請見《認領者參與須知》。有任何問題：<b>onlinesanta2026@gmail.com</b>",
    "next": "開始認領禮物"
  }
]

export const DONOR_ONBOARD_ILLUSTRATIONS: Record<string, string> = {
  "tree": "<svg viewBox=\"0 0 400 380\" xmlns=\"http://www.w3.org/2000/svg\"><ellipse cx=\"200\" cy=\"352\" rx=\"150\" ry=\"18\" fill=\"rgba(255,255,255,.06)\"/><rect x=\"182\" y=\"290\" width=\"36\" height=\"52\" rx=\"8\" fill=\"#8B5A2B\"/><polygon points=\"200,180 80,300 320,300\" fill=\"#1F7A50\"/><polygon points=\"200,120 95,250 305,250\" fill=\"#2E9E6B\"/><polygon points=\"200,60 110,190 290,190\" fill=\"#3DB47C\"/><polygon points=\"200,24 211,54 242,54 217,72 226,102 200,85 174,102 183,72 158,54 189,54\" fill=\"#F2B134\"/><circle cx=\"172\" cy=\"170\" r=\"11\" fill=\"#E0523E\"/><circle cx=\"236\" cy=\"222\" r=\"11\" fill=\"#F2B134\"/><circle cx=\"150\" cy=\"262\" r=\"11\" fill=\"#E0523E\"/><circle cx=\"262\" cy=\"282\" r=\"11\" fill=\"#FFF7EC\"/><circle cx=\"200\" cy=\"300\" r=\"11\" fill=\"#F2B134\"/><rect x=\"46\" y=\"296\" width=\"76\" height=\"60\" rx=\"10\" fill=\"#E0523E\"/><rect x=\"78\" y=\"296\" width=\"12\" height=\"60\" fill=\"#F2B134\"/><rect x=\"40\" y=\"290\" width=\"88\" height=\"18\" rx=\"8\" fill=\"#B8402F\"/><rect x=\"286\" y=\"306\" width=\"64\" height=\"50\" rx=\"10\" fill=\"#F2B134\"/><rect x=\"312\" y=\"306\" width=\"12\" height=\"50\" fill=\"#E0523E\"/><rect x=\"280\" y=\"300\" width=\"76\" height=\"16\" rx=\"8\" fill=\"#D99A1F\"/></svg>",
  "banned": "<svg viewBox=\"0 0 400 380\" xmlns=\"http://www.w3.org/2000/svg\"><ellipse cx=\"200\" cy=\"352\" rx=\"150\" ry=\"18\" fill=\"rgba(255,255,255,.06)\"/><rect x=\"110\" y=\"200\" width=\"180\" height=\"140\" rx=\"14\" fill=\"#E0523E\"/><rect x=\"100\" y=\"182\" width=\"200\" height=\"44\" rx=\"12\" fill=\"#B8402F\"/><rect x=\"186\" y=\"182\" width=\"28\" height=\"158\" fill=\"#F2B134\"/><ellipse cx=\"168\" cy=\"168\" rx=\"28\" ry=\"17\" fill=\"#F2B134\"/><ellipse cx=\"232\" cy=\"168\" rx=\"28\" ry=\"17\" fill=\"#F2B134\"/><circle cx=\"200\" cy=\"170\" r=\"13\" fill=\"#D99A1F\"/><circle cx=\"78\" cy=\"92\" r=\"48\" fill=\"#FFF7EC\" stroke=\"#E0523E\" stroke-width=\"9\"/><circle cx=\"78\" cy=\"98\" r=\"21\" fill=\"#E0523E\"/><rect x=\"76\" y=\"68\" width=\"5\" height=\"14\" rx=\"2\" fill=\"#8B5A2B\"/><ellipse cx=\"90\" cy=\"74\" rx=\"9\" ry=\"4.5\" fill=\"#2E9E6B\"/><line x1=\"46\" y1=\"60\" x2=\"110\" y2=\"124\" stroke=\"#E0523E\" stroke-width=\"9\" stroke-linecap=\"round\"/><circle cx=\"200\" cy=\"72\" r=\"48\" fill=\"#FFF7EC\" stroke=\"#E0523E\" stroke-width=\"9\"/><rect x=\"170\" y=\"56\" width=\"60\" height=\"32\" rx=\"5\" fill=\"#2E9E6B\"/><circle cx=\"200\" cy=\"72\" r=\"9\" fill=\"#FFF7EC\"/><line x1=\"168\" y1=\"40\" x2=\"232\" y2=\"104\" stroke=\"#E0523E\" stroke-width=\"9\" stroke-linecap=\"round\"/><circle cx=\"322\" cy=\"92\" r=\"48\" fill=\"#FFF7EC\" stroke=\"#E0523E\" stroke-width=\"9\"/><polygon points=\"322,62 350,112 294,112\" fill=\"#F2B134\"/><rect x=\"318.5\" y=\"76\" width=\"7\" height=\"20\" rx=\"3.5\" fill=\"#1F2A44\"/><circle cx=\"322\" cy=\"104\" r=\"4\" fill=\"#1F2A44\"/><line x1=\"290\" y1=\"60\" x2=\"354\" y2=\"124\" stroke=\"#E0523E\" stroke-width=\"9\" stroke-linecap=\"round\"/></svg>",
  "ship": "<svg viewBox=\"0 0 400 380\" xmlns=\"http://www.w3.org/2000/svg\"><ellipse cx=\"200\" cy=\"352\" rx=\"150\" ry=\"18\" fill=\"rgba(255,255,255,.06)\"/><rect x=\"56\" y=\"190\" width=\"200\" height=\"140\" rx=\"12\" fill=\"#C98A4B\"/><rect x=\"56\" y=\"190\" width=\"200\" height=\"26\" fill=\"#A86F38\"/><rect x=\"142\" y=\"190\" width=\"30\" height=\"140\" fill=\"#D9A066\"/><rect x=\"74\" y=\"238\" width=\"96\" height=\"60\" rx=\"6\" fill=\"#FFF7EC\"/><rect x=\"86\" y=\"252\" width=\"56\" height=\"7\" rx=\"3.5\" fill=\"#64748B\"/><rect x=\"86\" y=\"267\" width=\"40\" height=\"7\" rx=\"3.5\" fill=\"#64748B\"/><rect x=\"86\" y=\"282\" width=\"70\" height=\"7\" rx=\"3.5\" fill=\"#CBD5E1\"/><rect x=\"262\" y=\"96\" width=\"104\" height=\"204\" rx=\"20\" fill=\"#1F2A44\" stroke=\"#475569\" stroke-width=\"6\"/><rect x=\"276\" y=\"118\" width=\"76\" height=\"152\" rx=\"10\" fill=\"#0d1526\"/><rect x=\"288\" y=\"132\" width=\"52\" height=\"52\" rx=\"8\" fill=\"#2E9E6B\"/><circle cx=\"314\" cy=\"158\" r=\"12\" fill=\"#0d1526\"/><circle cx=\"314\" cy=\"158\" r=\"6\" fill=\"#6EE7B7\"/><rect x=\"304\" y=\"140\" width=\"8\" height=\"5\" rx=\"2\" fill=\"#0d1526\"/><path d=\"M314 258 v-44\" stroke=\"#6EE7B7\" stroke-width=\"7\" stroke-linecap=\"round\"/><polyline points=\"298,230 314,214 330,230\" fill=\"none\" stroke=\"#6EE7B7\" stroke-width=\"7\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/></svg>",
  "balance": "<svg viewBox=\"0 0 400 380\" xmlns=\"http://www.w3.org/2000/svg\"><ellipse cx=\"200\" cy=\"352\" rx=\"150\" ry=\"18\" fill=\"rgba(255,255,255,.06)\"/><rect x=\"190\" y=\"124\" width=\"20\" height=\"208\" rx=\"8\" fill=\"#64748B\"/><rect x=\"130\" y=\"322\" width=\"140\" height=\"22\" rx=\"11\" fill=\"#475569\"/><rect x=\"60\" y=\"112\" width=\"280\" height=\"14\" rx=\"7\" fill=\"#94A3B8\"/><circle cx=\"200\" cy=\"119\" r=\"16\" fill=\"#F2B134\"/><line x1=\"88\" y1=\"124\" x2=\"56\" y2=\"206\" stroke=\"#94A3B8\" stroke-width=\"4\"/><line x1=\"88\" y1=\"124\" x2=\"120\" y2=\"206\" stroke=\"#94A3B8\" stroke-width=\"4\"/><ellipse cx=\"88\" cy=\"210\" rx=\"52\" ry=\"13\" fill=\"#64748B\"/><rect x=\"62\" y=\"158\" width=\"52\" height=\"44\" rx=\"6\" fill=\"#E0523E\"/><rect x=\"82\" y=\"158\" width=\"12\" height=\"44\" fill=\"#F2B134\"/><rect x=\"56\" y=\"150\" width=\"64\" height=\"14\" rx=\"6\" fill=\"#B8402F\"/><line x1=\"312\" y1=\"124\" x2=\"280\" y2=\"206\" stroke=\"#94A3B8\" stroke-width=\"4\"/><line x1=\"312\" y1=\"124\" x2=\"344\" y2=\"206\" stroke=\"#94A3B8\" stroke-width=\"4\"/><ellipse cx=\"312\" cy=\"210\" rx=\"52\" ry=\"13\" fill=\"#64748B\"/><path d=\"M312 202 l-24 -24 a15 15 0 0 1 24 -20 a15 15 0 0 1 24 20 z\" fill=\"#E0523E\"/></svg>",
  "hat": "<svg viewBox=\"0 0 400 380\" xmlns=\"http://www.w3.org/2000/svg\"><ellipse cx=\"200\" cy=\"352\" rx=\"150\" ry=\"18\" fill=\"rgba(255,255,255,.06)\"/><path d=\"M104 270 C 112 160, 220 70, 296 86 C 334 94, 338 124, 306 134 C 262 136, 236 196, 292 270 Z\" fill=\"#E0523E\"/><rect x=\"76\" y=\"256\" width=\"246\" height=\"60\" rx=\"30\" fill=\"#FFF7EC\"/><circle cx=\"308\" cy=\"98\" r=\"30\" fill=\"#FFF7EC\"/><polygon points=\"66,110 75,132 98,140 75,148 66,170 57,148 34,140 57,132\" fill=\"#F2B134\"/><polygon points=\"350,196 356,210 370,216 356,222 350,236 344,222 330,216 344,210\" fill=\"#F2B134\"/><polygon points=\"120,60 124,70 134,74 124,78 120,88 116,78 106,74 116,70\" fill=\"#6EE7B7\"/></svg>"
}
