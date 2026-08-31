/** 機構申請兩個步驟共用的小東西。 */

/** 步驟標題。兩頁都要有，使用者才知道自己在哪裡、還有幾步。 */
export function StepHeading({ step, title }: { step: 1 | 2; title: string }) {
  return (
    <div>
      <p className="text-sm font-medium text-santa-600">步驟 {step}／2</p>
      <h1 className="mt-1 text-2xl font-bold text-slate-800">{title}</h1>
    </div>
  )
}
