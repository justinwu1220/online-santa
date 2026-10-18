import { EASE_OUT, burst, center, pop, reducedMotion, rise, wait } from './motion'

/**
 * 認領後頁面的兩段動畫（SPEC 11.3、11.4）。都是疊在「預設看得見」的內容上的效果，
 * 中途換頁、取消或被中斷，內容都維持可見。呼叫端負責在換頁時 `clearEffects()`。
 */

const pick = (root: ParentNode, selector: string) => root.querySelector(selector)
const pickAll = (root: ParentNode, selector: string) => [...root.querySelectorAll(selector)]

/**
 * 認領成功：右上標籤淡入、進度淡入、第一步勾彈出、緞帶從中間往兩側拉開（0.7s）、蝴蝶結旋轉彈出、
 * 「謝謝你成為…」依序浮上、期限提示與各面板由下 24px 依序浮上（每張間隔 0.09s）；
 * 緞帶在畫面下半部時平滑捲過去，0.9s 後在蝴蝶結位置再撒一次星星。
 */
export function playCelebration(root: HTMLElement) {
  if (reducedMotion()) {
    pick(root, '.task')?.scrollIntoView({ block: 'start' })
    return
  }

  pick(root, '.wish .status-tab')?.animate(
    [{ opacity: 0, transform: 'translateY(-10px)' }, { opacity: 1, transform: 'none' }],
    { duration: 420, easing: EASE_OUT, fill: 'backwards' })
  rise([pick(root, '.progress')], { delay: 60, stagger: 0, dist: 14, dur: 500 })
  pop(pick(root, '.progress .step .step__dot'), 300)

  pickAll(root, '.ribbon__line').forEach((line) => {
    line.animate(
      [{ transform: 'scaleX(0)', opacity: 0 }, { transform: 'scaleX(1)', opacity: 1 }],
      { duration: 700, delay: 380, easing: EASE_OUT, fill: 'backwards' })
  })
  const bow = pick(root, '.ribbon__bow')
  bow?.animate([
    { opacity: 0, transform: 'scale(.3) rotate(-25deg)' },
    { opacity: 1, transform: 'scale(1.18) rotate(8deg)', offset: 0.6 },
    { opacity: 1, transform: 'none' },
  ], { duration: 700, delay: 300, easing: 'ease-out', fill: 'backwards' })

  rise([pick(root, '.task__thanks'), pick(root, '.task__sub')], { delay: 620, stagger: 90, dist: 16, dur: 550 })
  rise(
    [pick(root, '.deadline'), ...pickAll(root, '.task__main > .panel'), ...pickAll(root, '.task__side > .panel')],
    { delay: 820, stagger: 90 })

  const ribbon = pick(root, '.ribbon')
  if (ribbon) {
    const top = ribbon.getBoundingClientRect().top + window.scrollY - 32
    if (top > window.scrollY + window.innerHeight * 0.35) window.scrollTo({ top, behavior: 'smooth' })
  }
  window.setTimeout(() => {
    if (bow && bow.isConnected) burst(...center(bow), 14, 90)
  }, 900)
}

/**
 * 取消認領：卡片由下往上依序下沉淡出、緞帶往中間收回、蝴蝶結轉開消失（禮物被收回）。
 * 比認領安靜：不撒星星、不彈跳。回傳時動畫已經播完（約 0.84s，用計時器等）。
 */
export async function playCancelOut(root: HTMLElement) {
  if (reducedMotion()) return

  const out: KeyframeAnimationOptions = { duration: 380, easing: 'cubic-bezier(.5,0,.75,0)', fill: 'forwards' }
  const cards = [
    ...pickAll(root, '.task__side > .panel').reverse(),
    ...pickAll(root, '.task__main > .panel').reverse(),
    pick(root, '.deadline'), pick(root, '.task__sub'), pick(root, '.task__thanks'),
  ]
  cards.forEach((el, index) => {
    el?.animate(
      [{ opacity: 1, transform: 'none' }, { opacity: 0, transform: 'translateY(28px) scale(.98)' }],
      { ...out, delay: index * 45 })
  })

  pickAll(root, '.ribbon__line').forEach((line) => {
    line.animate(
      [{ transform: 'scaleX(1)', opacity: 1 }, { transform: 'scaleX(0)', opacity: 0 }],
      { duration: 480, delay: 260, easing: 'ease-in', fill: 'forwards' })
  })
  pick(root, '.ribbon__bow')?.animate([
    { opacity: 1, transform: 'none' },
    { opacity: 1, transform: 'scale(1.1) rotate(-10deg)', offset: 0.3 },
    { opacity: 0, transform: 'scale(.2) rotate(40deg)' },
  ], { duration: 520, delay: 300, easing: 'ease-in', fill: 'forwards' })
  pick(root, '.progress')?.animate(
    [{ opacity: 1 }, { opacity: 0, transform: 'translateY(8px)' }],
    { duration: 300, delay: 200, easing: 'ease-in', fill: 'forwards' })
  pick(root, '.wish .status-tab')?.animate(
    [{ opacity: 1 }, { opacity: 0 }], { duration: 260, delay: 300, fill: 'forwards' })

  await wait(840)
}
