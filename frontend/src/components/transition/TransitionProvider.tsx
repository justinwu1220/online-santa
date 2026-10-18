import { useEffect, useRef, type ReactNode } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import {
  clearEffects, consumeSpecialNavigation, isCurrentNav, isDetailPath, isListPath, listReturn,
  markEnvelopeStarted, markSpecialNavigation, reducedMotion, rise, saveScroll, startNav, wait,
} from '../../lib/motion'
import { EnvelopeOverlay } from './EnvelopeOverlay'

/**
 * 換頁轉場（SPEC 6）。放在 PublicLayout，頁首與雪花在它之外，換頁時不重新產生。
 *
 * 所有站內連結的點擊在這裡統一接手（document 的捕獲階段，早於 React 的連結處理）：
 *
 * - **卡片 → 詳細頁**：拆信轉場。先讓遮罩在「目前頁面」上淡入（0.22s，頁首不被蓋住），完全不透明後
 *   才換頁；信封浮現、封口翻開、信紙升起；0.73s 時遮罩淡出，內容同時浮上（內容的浮上由詳細頁
 *   自己用 `envelopeRiseDelay()` 對齊）。
 * - **詳細頁 → 列表**：詳細頁淡出並下移 8px（0.18s）→ 換頁；列表自己淡入並還原捲動位置。
 * - **其他換頁**：目前頁淡出下移 8px（0.18s）→ 換頁 → 新頁由下 16px 浮上（0.45s）。
 *
 * 導覽的可靠性：點擊一定有反應（自己處理後再換頁，不依賴網址有沒有變）；較新的導覽取代進行中的
 * 轉場；等待用計時器；內容預設就是看得見的。轉場進行中遮罩擋住點擊，避免點到下方還沒浮上的內容。
 */
export function TransitionProvider({ children }: { children: ReactNode }) {
  const navigate = useNavigate()
  const location = useLocation()
  const overlay = useRef<HTMLDivElement>(null)
  const pathRef = useRef(location.pathname)
  const navigateRef = useRef(navigate)
  const firstRender = useRef(true)

  useEffect(() => {
    pathRef.current = location.pathname
    navigateRef.current = navigate
  })

  // 每次換頁之後：沒有專屬轉場的，新頁由下 16px 浮上；也負責回到頂端與清掉過期的「返回列表」標記
  const lastPath = useRef(location.pathname)
  const celebrating = Boolean((location.state as { celebrate?: boolean } | null)?.celebrate)
  useEffect(() => {
    const samePage = lastPath.current === location.pathname
    lastPath.current = location.pathname
    if (firstRender.current) {
      firstRender.current = false
      return
    }
    // 同一頁只是換了 state（例如認領頁把「剛認領」的標記清掉）：不是換頁，不能把捲動位置重設
    if (samePage) return
    if (!isListPath(location.pathname)) listReturn.pending = false
    // 剛認領成功：從原本的捲動位置接著平滑捲到緞帶（原型的行為），不先跳回頂端再往下捲
    if (!listReturn.pending && !celebrating) window.scrollTo(0, 0)
    if (consumeSpecialNavigation()) return
    rise([document.querySelector('.rd .page')], { dist: 16, dur: 450 })
  }, [location.key, location.pathname, celebrating])

  useEffect(() => {
    const resetOverlay = () => overlay.current?.classList.remove('is-on', 'is-visible', 'is-playing')

    const setOverlayTop = () => {
      const header = document.querySelector('.rd .site-header')
      overlay.current?.style.setProperty('--overlay-top', `${Math.max(0, header?.getBoundingClientRect().bottom ?? 0)}px`)
    }

    /** 目前頁淡出並下移 8px（0.18s）。 */
    const fadeOutPage = async (serial: number) => {
      document.querySelector('.rd .page')?.animate(
        [{ opacity: 1, transform: 'none' }, { opacity: 0, transform: 'translateY(8px)' }],
        { duration: 180, easing: 'ease-in', fill: 'forwards' })
      await wait(180)
      if (!isCurrentNav(serial)) return false
      clearEffects()
      return true
    }

    async function openWish(to: string, child: string | undefined) {
      const serial = startNav()
      const element = overlay.current
      if (reducedMotion() || !element) {
        markSpecialNavigation()
        navigateRef.current(to)
        return
      }
      if (isListPath(pathRef.current)) saveScroll(pathRef.current)
      clearEffects()
      markSpecialNavigation()
      const label = element.querySelector('.env__label')
      if (label) label.textContent = child ? `打開${child}的信` : '打開信件'

      // 1. 遮罩在列表上淡入（0.22s），頁首不被蓋住
      resetOverlay()
      setOverlayTop()
      element.style.transitionDuration = '.22s'
      element.classList.add('is-on')
      void element.offsetWidth
      element.classList.add('is-visible')
      await wait(230)
      if (!isCurrentNav(serial)) return

      // 2. 遮罩完全不透明才換頁；信封浮現、封口翻開、信紙升起
      markEnvelopeStarted()
      navigateRef.current(to)
      window.scrollTo(0, 0)
      setOverlayTop()
      void element.offsetWidth
      element.classList.add('is-playing')
      await wait(730)
      if (!isCurrentNav(serial)) return

      // 3. 遮罩淡出（0.45s），內容同時浮上
      element.style.transitionDuration = '.45s'
      element.classList.remove('is-visible')
      await wait(460)
      if (isCurrentNav(serial)) resetOverlay()
    }

    async function leavePage(to: string) {
      const serial = startNav()
      resetOverlay()
      const fromDetail = isDetailPath(pathRef.current)
      const toList = isListPath(to.split(/[?#]/)[0])
      if (isListPath(pathRef.current)) saveScroll(pathRef.current)

      if (reducedMotion()) {
        navigateRef.current(to)
        return
      }
      if (!(await fadeOutPage(serial))) return
      if (fromDetail && toList) {
        listReturn.pending = true
        markSpecialNavigation()
      }
      navigateRef.current(to)
    }

    const plainClick = (event: MouseEvent) =>
      event.button === 0 && !event.metaKey && !event.ctrlKey && !event.shiftKey && !event.altKey

    function onClick(event: MouseEvent) {
      if (event.defaultPrevented || !plainClick(event)) return
      const anchor = (event.target as Element | null)?.closest<HTMLAnchorElement>('a[href]')
      if (!anchor || !anchor.closest('.rd')) return
      if ((anchor.target && anchor.target !== '_self') || anchor.hasAttribute('download')) return

      // 站內連結才接手：外部網址、mailto、頁內錨點（只捲動、不換頁）一律照預設
      const href = anchor.getAttribute('href') ?? ''
      if (!href.startsWith('/') || href.startsWith('//')) return
      const url = new URL(href, window.location.origin)
      const to = url.pathname + url.search + url.hash
      if (url.pathname + url.search === window.location.pathname + window.location.search) return

      event.preventDefault()
      event.stopPropagation()
      if (anchor.classList.contains('wish-card')) void openWish(to, anchor.dataset.child)
      else void leavePage(to)
    }

    document.addEventListener('click', onClick, true)
    return () => document.removeEventListener('click', onClick, true)
  }, [])

  return (
    <>
      {children}
      <EnvelopeOverlay ref={overlay} />
    </>
  )
}
