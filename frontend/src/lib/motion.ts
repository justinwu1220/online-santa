/**
 * 動畫工具（SPEC 6.3）。全部是「疊在可見內容上的暫時效果」：
 *
 * - **內容預設就是看得見的**。用 Web Animations 且 `fill: 'backwards'`——只在延遲期間維持起始狀態，
 *   動畫結束、被取消或沒播，元素都回到原本（看得見）的樣子。不要寫成「CSS 預設 opacity:0，
 *   加上某個 class 才播動畫」，那只要 class 有一次沒加上，內容就永遠是透明的。
 * - **等待一律用計時器**，不等 `animationend`／`Animation.finished`：分頁切到背景或動畫被中斷時，
 *   那些事件可能永遠不會觸發，整個網站就會卡住。
 * - **較新的導覽取代進行中的轉場**：每次導覽取一個序號，每個等待之後先確認序號還是最新的。
 * - 系統開啟「減少動態效果」時，所有效果直接不執行。
 */
export const EASE_OUT = 'cubic-bezier(.22,.8,.3,1)'

export const reducedMotion = () =>
  typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches

export const wait = (ms: number) => new Promise<void>((resolve) => { window.setTimeout(resolve, ms) })

// ------------------------------------------------------------ 導覽序號

let navSerial = 0

/** 開始一次新的導覽／轉場，回傳它的序號。 */
export const startNav = () => ++navSerial

/** 這個序號是不是目前最新的導覽；不是的話，轉場該立刻停手。 */
export const isCurrentNav = (serial: number) => serial === navSerial

// ------------------------------------------------------------ 跨元件的轉場狀態

/** 拆信轉場開始的時間。詳細頁用它算「大卡片該晚多久才浮上」，好跟遮罩淡出對齊。 */
let envelopeStartedAt = 0
export const ENVELOPE_RISE_AT = 730

export const markEnvelopeStarted = () => { envelopeStartedAt = Date.now() }

/** 拆信轉場還要多久才輪到內容浮上；沒有在轉場中就是 0。 */
export function envelopeRiseDelay(): number {
  if (!envelopeStartedAt) return 0
  const elapsed = Date.now() - envelopeStartedAt
  return elapsed > ENVELOPE_RISE_AT + 600 ? 0 : Math.max(0, ENVELOPE_RISE_AT - elapsed)
}

/**
 * 這次換頁已經有專屬的轉場或動畫（拆信、返回、認領成功），
 * 不要再疊上一般換頁的「由下浮上」。由 PublicLayout 在換頁後讀取並清掉。
 */
let specialNavigation = false
export const markSpecialNavigation = () => { specialNavigation = true }
export function consumeSpecialNavigation(): boolean {
  const was = specialNavigation
  specialNavigation = false
  return was
}

/** 剛從詳細頁回到列表：列表要還原捲動位置、淡入，而不是回到頂端。 */
export const listReturn = { pending: false }

/** 離開列表前的捲動位置，key 是列表的 pathname。 */
const scrollMemory = new Map<string, number>()
export const saveScroll = (key: string) => { scrollMemory.set(key, window.scrollY) }
export const savedScroll = (key: string) => scrollMemory.get(key) ?? 0

/** 列表頁（願望牆、我的認領）。 */
export const isListPath = (path: string) => path === '/' || path === '/me/claims'
/** 詳細頁（願望詳細、認領後頁面）。 */
export const isDetailPath = (path: string) => /^\/wishes\/[^/]+$/.test(path) || /^\/me\/claims\/[^/]+$/.test(path)

// ------------------------------------------------------------ 效果

type Target = Element | null | undefined

/** 由下浮上。`dist` 是起始位移（px）。 */
export function rise(
  els: Target[],
  { delay = 0, stagger = 80, dist = 24, dur = 600 }: {
    delay?: number; stagger?: number; dist?: number; dur?: number
  } = {},
) {
  if (reducedMotion()) return
  els.filter((el): el is Element => Boolean(el)).forEach((el, index) => {
    el.animate(
      [{ opacity: 0, transform: `translateY(${dist}px)` }, { opacity: 1, transform: 'none' }],
      { duration: dur, delay: delay + index * stagger, easing: EASE_OUT, fill: 'backwards' })
  })
}

/** 彈出：先放大過頭再回到原大小。 */
export function pop(el: Target, delay = 0) {
  if (!el || reducedMotion()) return
  el.animate(
    [
      { transform: 'scale(0)', opacity: 0 },
      { transform: 'scale(1.3)', opacity: 1, offset: 0.6 },
      { transform: 'scale(1)', opacity: 1 },
    ],
    { duration: 520, delay, easing: 'ease-out', fill: 'backwards' })
}

// 星星與雪點從一點向外散開
const FX_COLORS = ['#fde68a', '#facc15', '#ffffff', '#6ee7b7', '#fca5a5']
const STAR = (color: string) =>
  `<svg viewBox="0 0 24 24"><path fill="${color}" d="M12 0c1 6.5 5.5 11 12 12-6.5 1-11 5.5-12 12-1-6.5-5.5-11-12-12C6.5 11 11 6.5 12 0z"/></svg>`
const DOT = (color: string) => `<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="7" fill="${color}"/></svg>`

function fxLayer(): HTMLElement | null {
  const root = document.querySelector<HTMLElement>('.rd')
  if (!root) return null
  let layer = root.querySelector<HTMLElement>(':scope > .fx-layer')
  if (!layer) {
    layer = document.createElement('div')
    layer.className = 'fx-layer'
    layer.setAttribute('aria-hidden', 'true')
    root.appendChild(layer)
  }
  return layer
}

/** 在 (x, y)（視窗座標）撒出星星與雪點，帶一點重力、旋轉、淡出。節點用計時器清掉。 */
export function burst(x: number, y: number, count = 18, spread = 120, delay = 0) {
  if (reducedMotion()) return
  const layer = fxLayer()
  if (!layer) return

  for (let i = 0; i < count; i += 1) {
    const el = document.createElement('span')
    el.className = 'fx'
    const color = FX_COLORS[i % FX_COLORS.length]
    el.innerHTML = i % 3 ? STAR(color) : DOT(color)
    const size = 8 + Math.random() * 10
    el.style.cssText = `left:${x}px;top:${y}px;width:${size}px;height:${size}px;margin:${-size / 2}px 0 0 ${-size / 2}px`
    layer.append(el)

    const angle = (i / count) * Math.PI * 2 + Math.random() * 0.5
    const distance = spread * (0.55 + Math.random() * 0.6)
    const dx = Math.cos(angle) * distance
    const dy = Math.sin(angle) * distance - 20
    const duration = 850 + Math.random() * 450
    el.animate([
      { transform: 'translate(0,0) scale(.2) rotate(0deg)', opacity: 0 },
      { transform: `translate(${dx * 0.7}px,${dy * 0.7}px) scale(1) rotate(90deg)`, opacity: 1, offset: 0.35 },
      { transform: `translate(${dx}px,${dy + 36}px) scale(.6) rotate(200deg)`, opacity: 0 },
    ], { duration, delay, easing: 'cubic-bezier(.2,.7,.3,1)', fill: 'both' })
    window.setTimeout(() => el.remove(), delay + duration + 100)
  }
}

export function center(el: Element): [number, number] {
  const rect = el.getBoundingClientRect()
  return [rect.left + rect.width / 2, rect.top + rect.height / 2]
}

/**
 * 取消所有由程式加上的效果，內容回到預設的可見狀態。
 * 保留 CSS 動畫與 CSS 過渡（閃爍、信封），以及星星層與提示。
 */
export function clearEffects() {
  document.getAnimations().forEach((animation) => {
    if (typeof CSSAnimation !== 'undefined' && animation instanceof CSSAnimation) return
    if (typeof CSSTransition !== 'undefined' && animation instanceof CSSTransition) return
    const target = (animation.effect as KeyframeEffect | null)?.target
    if (target instanceof Element && target.closest('.fx-layer, .toast')) return
    animation.cancel()
  })
}

/**
 * 清掉「已經播完、只是維持結尾狀態」的殘留動畫（`fill: forwards`／`both`）。
 *
 * 淡出類動畫（換頁前的淡出、選單切換的淡出、取消認領的收回）都用 `fill: forwards` 維持在透明，
 * 正常流程會在換頁後取消它；萬一某條路徑被較新的操作取代而漏掉，內容就會停在透明、看起來像點不到。
 * 這是全站的保險：換頁之後呼叫一次，把這類殘留清乾淨。CSS 動畫與提示、星星層不動。
 */
export function releaseStrandedEffects() {
  document.getAnimations().forEach((animation) => {
    if (typeof CSSAnimation !== 'undefined' && animation instanceof CSSAnimation) return
    if (typeof CSSTransition !== 'undefined' && animation instanceof CSSTransition) return
    if (animation.playState !== 'finished') return
    const effect = animation.effect as KeyframeEffect | null
    const fill = effect?.getTiming().fill
    if (fill !== 'forwards' && fill !== 'both') return
    const target = effect?.target
    if (target instanceof Element && target.closest('.fx-layer, .toast')) return
    animation.cancel()
  })
}

/**
 * 開啟 `<dialog>`（showModal）。元素還沒掛進文件（極少數 render 順序下會發生）時
 * 原生會丟 InvalidStateError，沒接住會讓整棵 React 樹崩潰、畫面全黑；
 * 所以這裡不丟例外，等下一個畫面再試，最多等 30 個畫面。
 */
export function openModal(element: HTMLDialogElement, tries = 30): void {
  if (element.open) return
  if (element.isConnected) {
    try { element.showModal() } catch { /* 已被別處開啟或移除，不需處理 */ }
    return
  }
  if (tries > 0) window.requestAnimationFrame(() => openModal(element, tries - 1))
}
