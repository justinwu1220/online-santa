import { releaseStrandedEffects } from './motion'

/**
 * 後台「右側內容點不動」的保險與診斷。
 *
 * 症狀：左側選單能切換，右側的抽屜、按鈕、選單卻都沒反應。原因都是「有東西蓋在右側內容上面接走了點擊」，
 * 通常是一個開著、卻看不見的 modal `<dialog>`（它會讓整頁其餘部分 inert）。
 * 這裡在點擊落在右側內容範圍、卻被別的東西接走時：記錄現場、關掉看不見的 dialog、清掉殘留動畫。
 */

const closingSince = new WeakMap<HTMLDialogElement, number>()
const STALE_CLOSING_MS = 1000

/** 開著、卻看不見（透明、display:none、移出畫面，或關閉動畫卡超過 1 秒）的 dialog。 */
export function isStaleDialog(dialog: HTMLDialogElement, now = Date.now()): boolean {
  if (!dialog.open) return false
  const style = getComputedStyle(dialog)
  if (style.display === 'none' || style.visibility === 'hidden' || Number(style.opacity) < 0.05) return true
  const rect = dialog.getBoundingClientRect()
  if (rect.width > 0 && (rect.left >= window.innerWidth || rect.right <= 0)) return true
  if (dialog.classList.contains('is-closing')) {
    const since = closingSince.get(dialog) ?? now
    closingSince.set(dialog, since)
    return now - since > STALE_CLOSING_MS
  }
  closingSince.delete(dialog)
  return false
}

/** 關掉所有看不見的開啟中 dialog，回傳關掉幾個。 */
export function closeStaleDialogs(root: ParentNode = document): number {
  let closed = 0
  root.querySelectorAll<HTMLDialogElement>('dialog[open]').forEach((dialog) => {
    if (!isStaleDialog(dialog)) return
    dialog.classList.remove('is-closing')
    dialog.close()
    closed += 1
  })
  return closed
}

/** 現場報告：誰接走了點擊、開著哪些 dialog、右側內容上有哪些帶 fill 的動畫。 */
export function diagnose(main: Element | null, point?: { x: number; y: number }) {
  const describe = (el: Element | null) => el ? `${el.tagName.toLowerCase()}.${String(el.className).trim().replace(/\s+/g, '.')}` : null
  return {
    path: location.pathname,
    topElement: point ? describe(document.elementFromPoint?.(point.x, point.y) ?? null) : null,
    dialogs: [...document.querySelectorAll<HTMLDialogElement>('dialog')].filter((d) => d.open).map((d) => {
      const style = getComputedStyle(d)
      return {
        class: d.className, modal: d.matches(':modal'), closing: d.classList.contains('is-closing'),
        opacity: style.opacity, display: style.display, transform: style.transform,
      }
    }),
    fillAnimations: main
      ? main.getAnimations({ subtree: true }).map((a) => `${a.playState}/${(a.effect as KeyframeEffect | null)?.getTiming().fill}`)
      : [],
    htmlStyle: document.documentElement.style.cssText,
  }
}

/**
 * 掛上點擊守門員，回傳解除函式。點擊落在 `main` 範圍內，卻被不在 `main` 裡、也不是
 * 「看得見的開啟中 dialog 或提示」的元素接走，就視為被擋住。
 */
export function installClickGuard(main: () => HTMLElement | null): () => void {
  const onPointerDown = (event: PointerEvent) => {
    const area = main()
    const target = event.target
    if (!area || !(target instanceof Element) || area.contains(target)) return
    const rect = area.getBoundingClientRect()
    const inside = event.clientX >= rect.left && event.clientX <= rect.right
      && event.clientY >= rect.top && event.clientY <= rect.bottom
    if (!inside) return
    if (target.closest('.toast')) return
    const dialog = target.closest('dialog')
    if (dialog && dialog.open && !isStaleDialog(dialog)) return   // 正常的彈窗或抽屜

    console.warn('[click-guard] 右側內容的點擊被擋住，已嘗試復原', diagnose(area, { x: event.clientX, y: event.clientY }))
    closeStaleDialogs()
    releaseStrandedEffects()
  }
  document.addEventListener('pointerdown', onPointerDown, true)
  const diag = () => diagnose(main())
  ;(window as unknown as { __consoleDiag?: () => unknown }).__consoleDiag = diag
  return () => {
    document.removeEventListener('pointerdown', onPointerDown, true)
    const w = window as unknown as { __consoleDiag?: () => unknown }
    if (w.__consoleDiag === diag) delete w.__consoleDiag
  }
}
