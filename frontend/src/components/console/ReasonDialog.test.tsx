// @vitest-environment jsdom
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ReasonDialog } from './ReasonDialog'

function setup(onSubmit = vi.fn().mockResolvedValue(undefined)) {
  const onClose = vi.fn()
  render(
    <ReasonDialog open title="退回「測試」？" body="說明" label="退回原因" minLength={5}
      tooShort="請寫下具體的退回原因（至少 5 個字）" okText="退回" onSubmit={onSubmit} onClose={onClose} />,
  )
  return { onSubmit, onClose, box: screen.getByLabelText(/退回原因/) as HTMLTextAreaElement }
}

describe('ReasonDialog（退回／凍結／停權的必填原因）', () => {
  it('空白或太短：顯示紅框與錯誤文字、聚焦原因欄，不送出', () => {
    const { onSubmit, box } = setup()
    fireEvent.click(screen.getByRole('button', { name: '退回' }))

    expect(screen.getByRole('alert')).toHaveTextContent('請寫下具體的退回原因（至少 5 個字）')
    expect(box.closest('.field')).toHaveClass('is-invalid')
    expect(box).toHaveAttribute('aria-invalid', 'true')
    expect(document.activeElement).toBe(box)
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('開始輸入就清掉錯誤', () => {
    const { box } = setup()
    fireEvent.click(screen.getByRole('button', { name: '退回' }))
    expect(screen.getByRole('alert')).toBeInTheDocument()

    fireEvent.change(box, { target: { value: '立' } })
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(box.closest('.field')).not.toHaveClass('is-invalid')
  })

  it('原因夠長：以去掉前後空白的文字送出', async () => {
    const { onSubmit, box } = setup()
    fireEvent.change(box, { target: { value: '  影像模糊，請重新上傳  ' } })
    fireEvent.click(screen.getByRole('button', { name: '退回' }))
    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith('影像模糊，請重新上傳'))
  })

  it('送出失敗：把後端訊息顯示在欄位下方，按鈕恢復可按', async () => {
    const { box } = setup(vi.fn().mockRejectedValue(new Error('這個機構已被處理')))
    fireEvent.change(box, { target: { value: '足夠長的原因' } })
    fireEvent.click(screen.getByRole('button', { name: '退回' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('這個機構已被處理')
    expect(screen.getByRole('button', { name: '退回' })).toBeEnabled()
  })

  it('重新開啟時清空上次的內容與錯誤', () => {
    const onSubmit = vi.fn()
    const view = render(
      <ReasonDialog open title="t" body="b" label="退回原因" minLength={5} tooShort="太短" okText="退回"
        onSubmit={onSubmit} onClose={() => {}} />,
    )
    fireEvent.change(screen.getByLabelText(/退回原因/), { target: { value: '上次寫的' } })
    view.rerender(
      <ReasonDialog open={false} title="t" body="b" label="退回原因" minLength={5} tooShort="太短" okText="退回"
        onSubmit={onSubmit} onClose={() => {}} />,
    )
    view.rerender(
      <ReasonDialog open title="t" body="b" label="退回原因" minLength={5} tooShort="太短" okText="退回"
        onSubmit={onSubmit} onClose={() => {}} />,
    )
    expect(screen.getByLabelText(/退回原因/)).toHaveValue('')
  })
})
