import { useCallback, useState, type Dispatch, type SetStateAction } from 'react'

/**
 * 輸入草稿：抽屜與對話框關閉再打開時，保留使用者已經打的內容。
 *
 * 只存在這個模組的記憶體裡——重新整理頁面就消失，不寫 localStorage（草稿可能含孩子的資料，不留在瀏覽器上）。
 * 只有使用者改過內容才會有草稿；送出成功要 {@link clearDraft}，取消、關閉、按 Esc 都保留。
 * key 要帶上對象（哪個願望、哪位使用者），不同對象的草稿互不影響。
 */
const drafts = new Map<string, unknown>()

export function hasDraft(key: string | undefined): boolean {
  return key !== undefined && drafts.has(key)
}

export function clearDraft(key: string | undefined) {
  if (key !== undefined) drafts.delete(key)
}

/**
 * 像 `useState`，但值會同步存成草稿。
 *
 * - 第一個回傳值與 setter 的用法同 `useState`；setter 會把值存成草稿。
 * - `reset(value)`：把狀態設成 `value`，並丟掉草稿（開啟時沒有草稿，就用它帶入初始內容）。
 * - `key` 是 `undefined` 時完全不存草稿，行為等同 `useState`。
 * - `key` 改變（換成另一個對象）時，改讀那個 key 的草稿，沒有就用 `initial`。
 */
export function useDraftState<T>(key: string | undefined, initial: T) {
  const read = () => (key !== undefined && drafts.has(key) ? drafts.get(key) as T : initial)
  const [state, setState] = useState<T>(read)
  const [lastKey, setLastKey] = useState(key)
  if (key !== lastKey) {
    setLastKey(key)
    setState(read())
  }

  const update: Dispatch<SetStateAction<T>> = useCallback((next) => {
    setState((current) => {
      const value = typeof next === 'function' ? (next as (current: T) => T)(current) : next
      if (key !== undefined) drafts.set(key, value)
      return value
    })
  }, [key])

  const reset = useCallback((value: T) => {
    clearDraft(key)
    setState(value)
  }, [key])

  return [state, update, reset] as const
}
