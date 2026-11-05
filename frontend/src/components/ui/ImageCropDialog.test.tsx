// @vitest-environment jsdom
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { useEffect, useRef } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { cropImage } from '../../lib/cropImage'
import { frameSize, ImageCropDialog, minZoomFor } from './ImageCropDialog'

// 裁切元件本身靠真實的圖片與版面，jsdom 跑不了：換成會立刻回報裁切範圍的假元件
function MockCropper({ onCropComplete, aspect }: { onCropComplete: (a: unknown, b: unknown) => void; aspect: number }) {
  // 只回報一次：onCropComplete 每次 render 都是新函式，不擋的話會一直重送而無限重繪
  const reported = useRef(false)
  useEffect(() => {
    if (reported.current) return
    reported.current = true
    onCropComplete({}, { x: 10, y: 20, width: 400 * aspect, height: 400 })
  }, [onCropComplete, aspect])
  return <div data-testid="cropper" data-aspect={aspect} />
}

vi.mock('react-easy-crop', () => ({ default: MockCropper }))
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

describe('minZoomFor（縮放下限：圖片短邊剛好等於裁切框）', () => {
  const frame = { width: 400, height: 300 }
  it('直式圖：寬度對齊裁切框的寬', () => {
    // 縮放 1 倍時圖片顯示 200 × 300（塞進舞台）→ 要放大到寬 400
    expect(minZoomFor(frame, { width: 200, height: 300 })).toBe(2)
  })
  it('橫式圖：高度對齊裁切框的高；已經比裁切框大就可以縮小', () => {
    expect(minZoomFor(frame, { width: 800, height: 600 })).toBe(0.5)
    expect(minZoomFor(frame, { width: 600, height: 300 })).toBe(1)
  })
  it('還沒載入圖片尺寸時維持 1 倍', () => {
    expect(minZoomFor(frame, { width: 0, height: 0 })).toBe(1)
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
