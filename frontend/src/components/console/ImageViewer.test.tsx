// @vitest-environment jsdom
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { AttachmentGallery, ImageGallery, PreviewImage } from './ImageViewer'

const images = [
  { id: 'a', url: '/a.jpg', alt: '第一張' },
  { id: 'b', url: '/b.jpg', alt: '第二張' },
  { id: 'c', url: '/c.jpg', alt: '第三張' },
]

const dialog = () => document.querySelector('dialog') as HTMLDialogElement
const shown = () => dialog().querySelector('img') as HTMLImageElement

describe('ImageGallery（縮圖與放大檢視）', () => {
  it('顯示縮圖；點擊在彈窗裡放大對應的那張，並鎖住頁面捲動', () => {
    render(<ImageGallery images={images} />)
    expect(screen.getAllByRole('button', { name: /放大檢視/ })).toHaveLength(3)
    expect(dialog()).not.toHaveAttribute('open')

    fireEvent.click(screen.getByRole('button', { name: '放大檢視：第二張' }))
    expect(dialog()).toHaveAttribute('open')
    expect(shown()).toHaveAttribute('src', '/b.jpg')
    expect(screen.getByText('2 / 3')).toBeInTheDocument()
    expect(document.documentElement.style.overflow).toBe('hidden')
  })

  it('關閉鈕與點背景都能關閉，關閉後解除捲動鎖定', async () => {
    render(<ImageGallery images={images} />)
    fireEvent.click(screen.getByRole('button', { name: '放大檢視：第一張' }))
    fireEvent.click(screen.getByRole('button', { name: '關閉' }))
    await waitFor(() => expect(dialog()).not.toHaveAttribute('open'), { timeout: 1000 })
    expect(document.documentElement.style.overflow).toBe('')

    fireEvent.click(screen.getByRole('button', { name: '放大檢視：第一張' }))
    fireEvent.click(dialog()) // 點到彈窗本身（背景區）
    await waitFor(() => expect(dialog()).not.toHaveAttribute('open'), { timeout: 1000 })
  })

  it('Esc 關閉', async () => {
    render(<ImageGallery images={images} />)
    fireEvent.click(screen.getByRole('button', { name: '放大檢視：第一張' }))
    dialog().dispatchEvent(new Event('cancel', { cancelable: true }))
    await waitFor(() => expect(dialog()).not.toHaveAttribute('open'), { timeout: 1000 })
  })

  it('多張時：箭頭與鍵盤左右鍵可切換，頭尾會循環', () => {
    render(<ImageGallery images={images} />)
    fireEvent.click(screen.getByRole('button', { name: '放大檢視：第一張' }))

    fireEvent.click(screen.getByRole('button', { name: '下一張' }))
    expect(shown()).toHaveAttribute('src', '/b.jpg')
    fireEvent.keyDown(window, { key: 'ArrowRight' })
    expect(shown()).toHaveAttribute('src', '/c.jpg')
    fireEvent.keyDown(window, { key: 'ArrowRight' })
    expect(shown()).toHaveAttribute('src', '/a.jpg') // 循環回第一張
    fireEvent.keyDown(window, { key: 'ArrowLeft' })
    expect(shown()).toHaveAttribute('src', '/c.jpg') // 往前循環到最後
    expect(screen.getByText('3 / 3')).toBeInTheDocument()
  })

  it('只有一張時沒有箭頭與頁碼', () => {
    render(<ImageGallery images={[images[0]]} />)
    fireEvent.click(screen.getByRole('button', { name: '放大檢視：第一張' }))
    expect(screen.queryByRole('button', { name: '下一張' })).not.toBeInTheDocument()
    expect(screen.queryByText(/\d \/ \d/)).not.toBeInTheDocument()
  })

  it('圖片載入失敗：縮圖顯示佔位字、放大時顯示無法載入，不留破圖', () => {
    render(<ImageGallery images={[images[0]]} />)
    fireEvent.error(screen.getByRole('button', { name: '放大檢視：第一張' }).querySelector('img')!)
    expect(screen.getByText('無法載入')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '放大檢視：第一張' }))
    fireEvent.error(shown())
    expect(screen.getByText('無法載入圖片')).toBeInTheDocument()
  })
})

describe('AttachmentGallery（附件：圖片縮圖、PDF 維持連結）', () => {
  const files = [
    { id: '1', url: '/1.png', contentType: 'image/png', fileName: '寄件單.png' },
    { id: '2', url: '/2.pdf', contentType: 'application/pdf', fileName: '立案證書.pdf' },
    { id: '3', url: '/3.jpg', contentType: 'image/jpeg' },
  ]

  it('圖片是縮圖按鈕、PDF 是開新分頁的檔名連結', () => {
    render(<AttachmentGallery files={files} altPrefix="附件" />)
    expect(screen.getByRole('button', { name: '放大檢視：寄件單.png' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '放大檢視：附件 2' })).toBeInTheDocument() // 沒檔名用前綴＋序號
    const link = screen.getByRole('link', { name: '立案證書.pdf' })
    expect(link).toHaveAttribute('href', '/2.pdf')
    expect(link).toHaveAttribute('target', '_blank')
  })

  it('放大時只在圖片之間切換（PDF 不在其中）', () => {
    render(<AttachmentGallery files={files} altPrefix="附件" />)
    fireEvent.click(screen.getByRole('button', { name: '放大檢視：寄件單.png' }))
    expect(screen.getByText('1 / 2')).toBeInTheDocument()
  })

  it('沒有任何檔案：什麼都不顯示', () => {
    const { container } = render(<AttachmentGallery files={[]} altPrefix="附件" />)
    expect(container).toBeEmptyDOMElement()
  })
})

describe('PreviewImage（本機預覽圖）', () => {
  it('沒有圖片：只顯示佔位內容，沒有可點擊的縮圖', () => {
    render(<PreviewImage src={null} alt="信件">佔位文字</PreviewImage>)
    expect(screen.getByText('佔位文字')).toBeInTheDocument()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })

  it('有圖片：點擊放大', () => {
    render(<PreviewImage src="blob:local-1" alt="信件">佔位文字</PreviewImage>)
    expect(screen.queryByText('佔位文字')).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '放大檢視：信件' }))
    expect(dialog()).toHaveAttribute('open')
    expect(shown()).toHaveAttribute('src', 'blob:local-1')
  })
})
