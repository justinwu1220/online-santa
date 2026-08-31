import { BRAND } from '../../lib/brand'

/** 機構申請兩個步驟共用的小東西。 */

/**
 * 步驟標題。兩頁都要有，使用者才知道自己在哪裡、還有幾步。
 *
 * 平台名稱也放在這裡：申請流程有一半的人是從機構入口點進來的，中間跨了兩個路由，
 * 少了它就會出現「我在填什麼平台的表單」這種疑問。
 */
export function StepHeading({ step, title }: { step: 1 | 2; title: string }) {
  return (
    <div>
      <p className="text-sm font-medium text-santa-600">{BRAND}</p>
      <p className="mt-1 text-xs font-medium text-slate-500">步驟 {step}／2</p>
      <h1 className="mt-0.5 text-2xl font-bold text-slate-800">{title}</h1>
    </div>
  )
}
