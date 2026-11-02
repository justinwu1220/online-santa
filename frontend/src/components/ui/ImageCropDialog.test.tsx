// @vitest-environment jsdom
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { useEffect } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { cropImage } from '../../lib/cropImage'
import { frameSize, ImageCropDialog } from './ImageCropDialog'

// 裁切元件本身靠真實的圖片與版面，jsdom 跑不了：換成會立刻回報裁切範圍的假元件
vi.mock('react-easy-crop', () => ({
  default: ({ onCropComplete, aspect }: { onCropComplete: (a: unknown, b: unknown) => void; aspect: number }) => {
    useEffect(() => { onCropComplete({}, { x: 10, y: 20, width: 400 * aspect, height: 400 }) }, [])
    return <div data-testid="cropper" data-aspect={aspect} />
  },
}))
vi.mock('../../lib/cropImage', () => ({ cropImage: vi.fn() }))

const file = new File(['x'], '信.png', { type: 'image/png' })

beforeEach(() => {
  vi.mocked(cropImage).mockReset()
  URL.createObjectURL = vi.fn(() => 'blob:fake')
  URL.revokeObjectURL = vi.fn()
})

describe('frameSize（舞台中央的固定比例裁切框）', () => {
  it('寬度受限時以寬為準，高度受限時以高為準，四周留邊', () => {
    expect(frameSize({ width: 500, height: 400 }, 4 / 3)).toEqual({ width: 430, height: 323 })
    expect(frameSize({ width: 800, height: 200 }, 4 / 3)).toEqual({ width: 229, height: 172 })
  })
})

describe('ImageCropDialog', () => {
  it('確定時以選的範圍、比例與輸出寬度裁切，回傳裁好的檔案', async () => {
    const cropped = new File(['y'], '信.jpg', { type: 'image/jpeg' })
    vi.mocked(cropImage).mockResolvedValue(cropped)
    const onConfirm = vi.fn()
    render(<ImageCropDialog file={file} aspect={4 / 3} outputWidth={1280} title="裁切信件照片"
      onCancel={() => {}} onConfirm={onConfirm} />)

    fireEvent.click(await screen.findByRole('button', { name: '確定裁切', hidden: true }))
    await waitFor(() => expect(onConfirm).toHaveBeenCalledWith(cropped))
    expect(cropImage).toHaveBeenCalledWith(file, { x: 10, y: 20, width: 400 * (4 / 3), height: 400 }, 1280, 4 / 3)
  })

  it('取消不會裁切也不會回傳檔案', async () => {
    const onCancel = vi.fn()
    const onConfirm = vi.fn()
    render(<ImageCropDialog file={file} aspect={3 / 2} outputWidth={1200} title="裁切機構照片"
      onCancel={onCancel} onConfirm={onConfirm} />)
    fireEvent.click(await screen.findByRole('button', { name: '取消', hidden: true }))
    expect(onCancel).toHaveBeenCalledTimes(1)
    expect(onConfirm).not.toHaveBeenCalled()
    expect(cropImage).not.toHaveBeenCalled()
  })

  it('這張圖沒辦法裁切時顯示錯誤，不回傳檔案', async () => {
    vi.mocked(cropImage).mockRejectedValue(new Error('壞圖'))
    const onConfirm = vi.fn()
    render(<ImageCropDialog file={file} aspect={4 / 3} outputWidth={1280} title="t" onCancel={() => {}} onConfirm={onConfirm} />)
    fireEvent.click(await screen.findByRole('button', { name: '確定裁切', hidden: true }))
    expect(await screen.findByText('這張圖片沒辦法裁切，請換一張再試')).toBeInTheDocument()
    expect(onConfirm).not.toHaveBeenCalled()
  })

  it('沒有檔案時不顯示裁切畫面', () => {
    render(<ImageCropDialog file={null} aspect={4 / 3} outputWidth={1280} title="t" onCancel={() => {}} onConfirm={() => {}} />)
    expect(screen.queryByTestId('cropper')).toBeNull()
  })
})
