// @vitest-environment jsdom
import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { LetterRestoreDialog } from './LetterRestoreDialog'

function renderDialog(letterMasked: boolean, onConfirm = vi.fn()) {
  render(
    <LetterRestoreDialog open title="解除凍結？" body="說明" okText="解除凍結" letterMasked={letterMasked}
      busy={false} onConfirm={onConfirm} onClose={() => {}} />,
  )
  return onConfirm
}

describe('LetterRestoreDialog', () => {
  it('信件沒有被屏蔽：沒有勾選，確認送出 restoreLetter=false', () => {
    const onConfirm = renderDialog(false)
    expect(screen.queryByRole('checkbox', { hidden: true })).toBeNull()
    fireEvent.click(screen.getByRole('button', { name: '解除凍結', hidden: true }))
    expect(onConfirm).toHaveBeenCalledWith(false)
  })

  it('信件被屏蔽：預設不勾（維持屏蔽），勾了才恢復', () => {
    const onConfirm = renderDialog(true)
    const box = screen.getByRole('checkbox', { hidden: true })
    expect(box).not.toBeChecked()
    fireEvent.click(screen.getByRole('button', { name: '解除凍結', hidden: true }))
    expect(onConfirm).toHaveBeenLastCalledWith(false)

    fireEvent.click(box)
    fireEvent.click(screen.getByRole('button', { name: '解除凍結', hidden: true }))
    expect(onConfirm).toHaveBeenLastCalledWith(true)
  })
})
