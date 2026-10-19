/**
 * 首次登入說明（「開始之前」彈窗）的內容：認領者版（5 張）與合作機構版（8 張）。
 *
 * 由 scripts 從 docs/style/1018 新版/prototype.html 的 ONBOARD 逐字產生，不要手改文字或插圖——
 * 要改請改設計稿再重新產生。插圖是靜態 SVG 字串、說明裡的 <b> 也是我們自己的靜態標記，
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

export const ORG_ONBOARD_CARDS: OnboardCard[] = [
  {
    "step": "開始之前",
    "illo": "tree",
    "title": "歡迎加入線上聖誕老人 2026",
    "sub": "開始刊登願望之前，有六件事想先告訴您。",
    "next": "開始"
  },
  {
    "step": "1 / 6",
    "illo": "banned",
    "title": "不收食物、現金，與危害孩子安全的物品",
    "sub": "完整清單在《合作機構參與須知》，請以那份為準。",
    "next": "下一步"
  },
  {
    "step": "2 / 6",
    "illo": "price",
    "title": "每份願望的禮物，以 1,000 元為原則",
    "sub": "刊登願望時請先確認預算，讓更多孩子的願望都能被認領。",
    "next": "下一步"
  },
  {
    "step": "3 / 6",
    "illo": "inspect",
    "title": "收到禮物，先拆開檢查",
    "sub": "看一下卡片內容，用針孔探測器掃一遍，確認沒問題再交給孩子。",
    "next": "下一步"
  },
  {
    "step": "4 / 6",
    "illo": "status",
    "title": "認領進度看網站，收到禮物記得回報",
    "sub": "被認領後可在網站查看進度、用站內訊息聯絡認領者。收到並檢查完，請在網站標記<b>已收到</b>。",
    "next": "下一步"
  },
  {
    "step": "5 / 6",
    "illo": "receipt",
    "title": "捐贈收據不用您開",
    "sub": "捐贈收據一律由<b>社團法人彰化縣康乃心關懷協會</b>開立，請勿向認領者索取收據所需的個人資料。",
    "next": "下一步"
  },
  {
    "step": "6 / 6",
    "illo": "alert",
    "title": "覺得怪怪的，直接聯絡我們",
    "sub": "打探孩子資訊、要求見面、要帳號，請先別回應，直接寫信給 <b>onlinesanta2026@gmail.com</b>。",
    "next": "下一步"
  },
  {
    "step": "準備好了",
    "illo": "hat",
    "title": "開始刊登孩子的願望吧",
    "sub": "完整規範請見《合作機構參與須知》。有任何問題：<b>onlinesanta2026@gmail.com</b>",
    "next": "開始刊登願望"
  }
]

export const ORG_ONBOARD_ILLUSTRATIONS: Record<string, string> = {
  "tree": "<svg viewBox=\"0 0 400 380\" xmlns=\"http://www.w3.org/2000/svg\"><ellipse cx=\"200\" cy=\"352\" rx=\"150\" ry=\"18\" fill=\"rgba(255,255,255,.06)\"/><rect x=\"182\" y=\"290\" width=\"36\" height=\"52\" rx=\"8\" fill=\"#8B5A2B\"/><polygon points=\"200,180 80,300 320,300\" fill=\"#1F7A50\"/><polygon points=\"200,120 95,250 305,250\" fill=\"#2E9E6B\"/><polygon points=\"200,60 110,190 290,190\" fill=\"#3DB47C\"/><polygon points=\"200,24 211,54 242,54 217,72 226,102 200,85 174,102 183,72 158,54 189,54\" fill=\"#F2B134\"/><circle cx=\"172\" cy=\"170\" r=\"11\" fill=\"#E0523E\"/><circle cx=\"236\" cy=\"222\" r=\"11\" fill=\"#F2B134\"/><circle cx=\"150\" cy=\"262\" r=\"11\" fill=\"#E0523E\"/><circle cx=\"262\" cy=\"282\" r=\"11\" fill=\"#FFF7EC\"/><circle cx=\"200\" cy=\"300\" r=\"11\" fill=\"#F2B134\"/><rect x=\"46\" y=\"296\" width=\"76\" height=\"60\" rx=\"10\" fill=\"#E0523E\"/><rect x=\"78\" y=\"296\" width=\"12\" height=\"60\" fill=\"#F2B134\"/><rect x=\"40\" y=\"290\" width=\"88\" height=\"18\" rx=\"8\" fill=\"#B8402F\"/><rect x=\"286\" y=\"306\" width=\"64\" height=\"50\" rx=\"10\" fill=\"#F2B134\"/><rect x=\"312\" y=\"306\" width=\"12\" height=\"50\" fill=\"#E0523E\"/><rect x=\"280\" y=\"300\" width=\"76\" height=\"16\" rx=\"8\" fill=\"#D99A1F\"/></svg>",
  "banned": "<svg viewBox=\"0 0 400 380\" xmlns=\"http://www.w3.org/2000/svg\"><ellipse cx=\"200\" cy=\"352\" rx=\"150\" ry=\"18\" fill=\"rgba(255,255,255,.06)\"/><rect x=\"110\" y=\"200\" width=\"180\" height=\"140\" rx=\"14\" fill=\"#E0523E\"/><rect x=\"100\" y=\"182\" width=\"200\" height=\"44\" rx=\"12\" fill=\"#B8402F\"/><rect x=\"186\" y=\"182\" width=\"28\" height=\"158\" fill=\"#F2B134\"/><ellipse cx=\"168\" cy=\"168\" rx=\"28\" ry=\"17\" fill=\"#F2B134\"/><ellipse cx=\"232\" cy=\"168\" rx=\"28\" ry=\"17\" fill=\"#F2B134\"/><circle cx=\"200\" cy=\"170\" r=\"13\" fill=\"#D99A1F\"/><circle cx=\"78\" cy=\"92\" r=\"48\" fill=\"#FFF7EC\" stroke=\"#E0523E\" stroke-width=\"9\"/><circle cx=\"78\" cy=\"98\" r=\"21\" fill=\"#E0523E\"/><rect x=\"76\" y=\"68\" width=\"5\" height=\"14\" rx=\"2\" fill=\"#8B5A2B\"/><ellipse cx=\"90\" cy=\"74\" rx=\"9\" ry=\"4.5\" fill=\"#2E9E6B\"/><line x1=\"46\" y1=\"60\" x2=\"110\" y2=\"124\" stroke=\"#E0523E\" stroke-width=\"9\" stroke-linecap=\"round\"/><circle cx=\"200\" cy=\"72\" r=\"48\" fill=\"#FFF7EC\" stroke=\"#E0523E\" stroke-width=\"9\"/><rect x=\"170\" y=\"56\" width=\"60\" height=\"32\" rx=\"5\" fill=\"#2E9E6B\"/><circle cx=\"200\" cy=\"72\" r=\"9\" fill=\"#FFF7EC\"/><line x1=\"168\" y1=\"40\" x2=\"232\" y2=\"104\" stroke=\"#E0523E\" stroke-width=\"9\" stroke-linecap=\"round\"/><circle cx=\"322\" cy=\"92\" r=\"48\" fill=\"#FFF7EC\" stroke=\"#E0523E\" stroke-width=\"9\"/><polygon points=\"322,62 350,112 294,112\" fill=\"#F2B134\"/><rect x=\"318.5\" y=\"76\" width=\"7\" height=\"20\" rx=\"3.5\" fill=\"#1F2A44\"/><circle cx=\"322\" cy=\"104\" r=\"4\" fill=\"#1F2A44\"/><line x1=\"290\" y1=\"60\" x2=\"354\" y2=\"124\" stroke=\"#E0523E\" stroke-width=\"9\" stroke-linecap=\"round\"/></svg>",
  "price": "<svg viewBox=\"0 0 400 380\" xmlns=\"http://www.w3.org/2000/svg\"><ellipse cx=\"200\" cy=\"352\" rx=\"150\" ry=\"18\" fill=\"rgba(255,255,255,.06)\"/><rect x=\"70\" y=\"190\" width=\"200\" height=\"150\" rx=\"14\" fill=\"#2E9E6B\"/><rect x=\"60\" y=\"172\" width=\"220\" height=\"44\" rx=\"12\" fill=\"#1F7A50\"/><rect x=\"156\" y=\"172\" width=\"28\" height=\"168\" fill=\"#F2B134\"/><ellipse cx=\"138\" cy=\"158\" rx=\"28\" ry=\"17\" fill=\"#F2B134\"/><ellipse cx=\"202\" cy=\"158\" rx=\"28\" ry=\"17\" fill=\"#F2B134\"/><circle cx=\"170\" cy=\"160\" r=\"13\" fill=\"#D99A1F\"/><path d=\"M170 160 C 230 150, 260 110, 300 96\" stroke=\"#C98A4B\" stroke-width=\"5\" fill=\"none\" stroke-linecap=\"round\"/><polygon points=\"250,60 370,40 384,150 290,180 236,140\" fill=\"#F2B134\" stroke=\"#D99A1F\" stroke-width=\"6\" stroke-linejoin=\"round\"/><circle cx=\"266\" cy=\"76\" r=\"9\" fill=\"#FFF7EC\" stroke=\"#D99A1F\" stroke-width=\"4\"/><text x=\"314\" y=\"124\" text-anchor=\"middle\" font-family='\"Zen Maru Gothic\",\"Noto Sans TC\",\"PingFang TC\",\"Microsoft JhengHei\",sans-serif' font-weight=\"900\" font-size=\"32\" fill=\"#1F2A44\" transform=\"rotate(-9 314 124)\">1,000 元</text></svg>",
  "inspect": "<svg viewBox=\"0 0 400 380\" xmlns=\"http://www.w3.org/2000/svg\"><ellipse cx=\"200\" cy=\"352\" rx=\"150\" ry=\"18\" fill=\"rgba(255,255,255,.06)\"/><rect x=\"100\" y=\"200\" width=\"200\" height=\"130\" rx=\"12\" fill=\"#C98A4B\"/><rect x=\"100\" y=\"200\" width=\"200\" height=\"28\" fill=\"#A86F38\"/><polygon points=\"100,200 62,146 192,146 200,200\" fill=\"#D9A066\"/><polygon points=\"300,200 338,146 208,146 200,200\" fill=\"#D9A066\"/><rect x=\"150\" y=\"214\" width=\"100\" height=\"70\" rx=\"6\" fill=\"#FFF7EC\" transform=\"rotate(-8 200 249)\"/><path d=\"M200 266 l-16 -16 a10 10 0 0 1 16 -13 a10 10 0 0 1 16 13 z\" fill=\"#E0523E\"/><circle cx=\"300\" cy=\"104\" r=\"46\" fill=\"#FFF7EC\" stroke=\"#E2E8F0\" stroke-width=\"10\"/><circle cx=\"300\" cy=\"104\" r=\"32\" fill=\"#9CD3F0\"/><line x1=\"334\" y1=\"138\" x2=\"376\" y2=\"180\" stroke=\"#E2E8F0\" stroke-width=\"16\" stroke-linecap=\"round\"/><rect x=\"36\" y=\"246\" width=\"60\" height=\"112\" rx=\"14\" fill=\"#334155\"/><rect x=\"48\" y=\"262\" width=\"36\" height=\"42\" rx=\"6\" fill=\"#2E9E6B\"/><circle cx=\"66\" cy=\"330\" r=\"8\" fill=\"#E0523E\"/><rect x=\"62\" y=\"220\" width=\"8\" height=\"28\" rx=\"4\" fill=\"#94A3B8\"/><circle cx=\"66\" cy=\"216\" r=\"8\" fill=\"#F2B134\"/><path d=\"M86 232 a26 26 0 0 1 0 -32\" stroke=\"#F2B134\" stroke-width=\"5\" fill=\"none\" stroke-linecap=\"round\"/><path d=\"M100 244 a44 44 0 0 1 0 -56\" stroke=\"#F2B134\" stroke-width=\"5\" fill=\"none\" stroke-linecap=\"round\"/></svg>",
  "status": "<svg viewBox=\"0 0 400 380\" xmlns=\"http://www.w3.org/2000/svg\"><ellipse cx=\"200\" cy=\"352\" rx=\"150\" ry=\"18\" fill=\"rgba(255,255,255,.06)\"/><rect x=\"56\" y=\"70\" width=\"288\" height=\"210\" rx=\"18\" fill=\"#FFF7EC\"/><rect x=\"56\" y=\"70\" width=\"288\" height=\"38\" rx=\"18\" fill=\"#E2E8F0\"/><rect x=\"56\" y=\"90\" width=\"288\" height=\"18\" fill=\"#E2E8F0\"/><circle cx=\"80\" cy=\"89\" r=\"6\" fill=\"#E0523E\"/><circle cx=\"100\" cy=\"89\" r=\"6\" fill=\"#F2B134\"/><circle cx=\"120\" cy=\"89\" r=\"6\" fill=\"#2E9E6B\"/><rect x=\"84\" y=\"134\" width=\"150\" height=\"16\" rx=\"8\" fill=\"#CBD5E1\"/><circle cx=\"302\" cy=\"142\" r=\"15\" fill=\"#2E9E6B\"/><polyline points=\"294,142 300,148 311,136\" fill=\"none\" stroke=\"#FFF7EC\" stroke-width=\"4\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/><rect x=\"84\" y=\"178\" width=\"190\" height=\"16\" rx=\"8\" fill=\"#CBD5E1\"/><circle cx=\"302\" cy=\"186\" r=\"15\" fill=\"#2E9E6B\"/><polyline points=\"294,186 300,192 311,180\" fill=\"none\" stroke=\"#FFF7EC\" stroke-width=\"4\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/><rect x=\"84\" y=\"222\" width=\"120\" height=\"16\" rx=\"8\" fill=\"#CBD5E1\"/><circle cx=\"302\" cy=\"230\" r=\"15\" fill=\"#F2B134\"/><rect x=\"299\" y=\"222\" width=\"6\" height=\"10\" rx=\"3\" fill=\"#1F2A44\"/><circle cx=\"302\" cy=\"237\" r=\"3\" fill=\"#1F2A44\"/><rect x=\"222\" y=\"262\" width=\"140\" height=\"66\" rx=\"22\" fill=\"#2E9E6B\"/><polygon points=\"246,324 234,352 280,328\" fill=\"#2E9E6B\"/><path d=\"M292 312 l-18 -18 a11 11 0 0 1 18 -14 a11 11 0 0 1 18 14 z\" fill=\"#FFF7EC\"/></svg>",
  "receipt": "<svg viewBox=\"0 0 400 380\" xmlns=\"http://www.w3.org/2000/svg\"><ellipse cx=\"200\" cy=\"352\" rx=\"150\" ry=\"18\" fill=\"rgba(255,255,255,.06)\"/><path d=\"M96 52 H304 V318 l-17 -13 l-17 13 l-17 -13 l-17 13 l-17 -13 l-17 13 l-17 -13 l-17 13 l-17 -13 l-17 13 l-17 -13 l-17 13 Z\" fill=\"#FFF7EC\"/><rect x=\"96\" y=\"52\" width=\"208\" height=\"44\" fill=\"#2E9E6B\"/><rect x=\"124\" y=\"68\" width=\"110\" height=\"12\" rx=\"6\" fill=\"#FFF7EC\"/><rect x=\"124\" y=\"124\" width=\"152\" height=\"12\" rx=\"6\" fill=\"#CBD5E1\"/><rect x=\"124\" y=\"152\" width=\"118\" height=\"12\" rx=\"6\" fill=\"#CBD5E1\"/><rect x=\"124\" y=\"180\" width=\"138\" height=\"12\" rx=\"6\" fill=\"#CBD5E1\"/><rect x=\"124\" y=\"214\" width=\"72\" height=\"14\" rx=\"7\" fill=\"#94A3B8\"/><rect x=\"214\" y=\"212\" width=\"62\" height=\"18\" rx=\"9\" fill=\"#F2B134\"/><circle cx=\"248\" cy=\"264\" r=\"30\" fill=\"none\" stroke=\"#E0523E\" stroke-width=\"6\" transform=\"rotate(-12 248 264)\"/><circle cx=\"248\" cy=\"264\" r=\"19\" fill=\"none\" stroke=\"#E0523E\" stroke-width=\"3\"/><polygon points=\"248,250 252,259 262,260 254,266 257,276 248,270 239,276 242,266 234,260 244,259\" fill=\"#E0523E\"/><rect x=\"296\" y=\"230\" width=\"74\" height=\"60\" rx=\"10\" fill=\"#E0523E\"/><rect x=\"326\" y=\"230\" width=\"14\" height=\"60\" fill=\"#F2B134\"/><rect x=\"290\" y=\"222\" width=\"86\" height=\"18\" rx=\"8\" fill=\"#B8402F\"/><ellipse cx=\"318\" cy=\"214\" rx=\"16\" ry=\"10\" fill=\"#F2B134\"/><ellipse cx=\"348\" cy=\"214\" rx=\"16\" ry=\"10\" fill=\"#F2B134\"/><circle cx=\"333\" cy=\"216\" r=\"8\" fill=\"#D99A1F\"/><circle cx=\"70\" cy=\"262\" r=\"34\" fill=\"#2E9E6B\"/><polyline points=\"54,262 66,274 88,250\" fill=\"none\" stroke=\"#FFF7EC\" stroke-width=\"8\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/></svg>",
  "alert": "<svg viewBox=\"0 0 400 380\" xmlns=\"http://www.w3.org/2000/svg\"><ellipse cx=\"200\" cy=\"352\" rx=\"150\" ry=\"18\" fill=\"rgba(255,255,255,.06)\"/><rect x=\"70\" y=\"44\" width=\"260\" height=\"196\" rx=\"44\" fill=\"#FFF7EC\"/><polygon points=\"128,236 104,292 186,240\" fill=\"#FFF7EC\"/><rect x=\"184\" y=\"84\" width=\"32\" height=\"94\" rx=\"16\" fill=\"#E0523E\"/><circle cx=\"200\" cy=\"208\" r=\"17\" fill=\"#E0523E\"/><rect x=\"196\" y=\"256\" width=\"168\" height=\"104\" rx=\"14\" fill=\"#FFF7EC\" stroke=\"#94A3B8\" stroke-width=\"8\"/><polyline points=\"196,266 280,322 364,266\" fill=\"none\" stroke=\"#94A3B8\" stroke-width=\"8\" stroke-linejoin=\"round\"/></svg>",
  "hat": "<svg viewBox=\"0 0 400 380\" xmlns=\"http://www.w3.org/2000/svg\"><ellipse cx=\"200\" cy=\"352\" rx=\"150\" ry=\"18\" fill=\"rgba(255,255,255,.06)\"/><path d=\"M104 270 C 112 160, 220 70, 296 86 C 334 94, 338 124, 306 134 C 262 136, 236 196, 292 270 Z\" fill=\"#E0523E\"/><rect x=\"76\" y=\"256\" width=\"246\" height=\"60\" rx=\"30\" fill=\"#FFF7EC\"/><circle cx=\"308\" cy=\"98\" r=\"30\" fill=\"#FFF7EC\"/><polygon points=\"66,110 75,132 98,140 75,148 66,170 57,148 34,140 57,132\" fill=\"#F2B134\"/><polygon points=\"350,196 356,210 370,216 356,222 350,236 344,222 330,216 344,210\" fill=\"#F2B134\"/><polygon points=\"120,60 124,70 134,74 124,78 120,88 116,78 106,74 116,70\" fill=\"#6EE7B7\"/></svg>"
}
