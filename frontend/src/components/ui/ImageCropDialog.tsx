import { useEffect, useRef, useState } from 'react'
import Cropper, { type Area } from 'react-easy-crop'
import { cropImage } from '../../lib/cropImage'
import { Dialog } from './Dialog'

const MIN_ZOOM = 1
const MAX_ZOOM = 4
// 裁切框佔舞台的比例：留一圈暗色邊，虛線框才看得出是「中央的固定尺寸」
const FRAME_FILL = 0.86

/** 在舞台裡放得下的最大裁切框（固定比例，四周留邊）。 */
export function frameSize(stage: { width: number; height: number }, aspect: number) {
  const width = Math.min(stage.width * FRAME_FILL, stage.height * FRAME_FILL * aspect)
  return { width: Math.round(width), height: Math.round(width / aspect) }
}

/**
 * 圖片裁切：裁切框尺寸（比例）固定，畫面中央用虛線標出；使用者拖曳或縮放圖片來決定留下哪一塊。
 * 確定後輸出固定尺寸的 JPEG——只上傳裁切結果，原圖不離開瀏覽器。
 *
 * @param file 要裁的圖片；`null` 時關閉
 * @param aspect 寬／高，例如 4/3
 * @param outputWidth 輸出寬度（像素），高度依比例算
 * @param safeArea 在裁切框內再用虛線標出「所有顯示處都看得到」的上下安全區（機構照片：認領頁只露出中間 2:1）
 */
export function ImageCropDialog({ file, aspect, outputWidth, title, hint, safeArea = false, onCancel, onConfirm }: {
  file: File | null
  aspect: number
  outputWidth: number
  title: string
  hint?: string
  safeArea?: boolean
  onCancel: () => void
  onConfirm: (cropped: File) => void
}) {
  const [url, setUrl] = useState<string>()
  const [crop, setCrop] = useState({ x: 0, y: 0 })
  const [zoom, setZoom] = useState(MIN_ZOOM)
  const [area, setArea] = useState<Area | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const stageRef = useRef<HTMLDivElement>(null)
  const [stage, setStage] = useState({ width: 0, height: 0 })

  // 舞台的實際大小（對話框打開後才有尺寸）
  useEffect(() => {
    const element = stageRef.current
    if (!element || !file || typeof ResizeObserver === 'undefined') return
    const observer = new ResizeObserver(() => setStage({ width: element.clientWidth, height: element.clientHeight }))
    observer.observe(element)
    return () => observer.disconnect()
  }, [file])

  useEffect(() => {
    if (!file) { setUrl(undefined); return }
    const objectUrl = URL.createObjectURL(file)
    setUrl(objectUrl)
    setCrop({ x: 0, y: 0 }); setZoom(MIN_ZOOM); setArea(null); setError(''); setBusy(false)
    return () => URL.revokeObjectURL(objectUrl)
  }, [file])

  async function confirm() {
    if (!file || !area || busy) return
    setBusy(true)
    try {
      onConfirm(await cropImage(file, area, outputWidth, aspect))
    } catch {
      setError('這張圖片沒辦法裁切，請換一張再試')
      setBusy(false)
    }
  }

  return (
    <Dialog open={file !== null} onClose={onCancel} dismissible={!busy} labelledBy="cropTitle" className="dialog crop-dialog">
      <h2 id="cropTitle">{title}</h2>
      <p className="crop-dialog__hint">
        {hint ?? '拖曳圖片移動位置，用滑桿或滾輪放大縮小；虛線框內就是會留下的範圍。'}
      </p>
      <div className="crop-stage" ref={stageRef}>
        {url && (
          <Cropper image={url} crop={crop} zoom={zoom} aspect={aspect} minZoom={MIN_ZOOM} maxZoom={MAX_ZOOM}
            showGrid={false} objectFit="cover"
            cropSize={stage.width > 0 ? frameSize(stage, aspect) : undefined}
            classes={{ cropAreaClassName: `crop-area${safeArea ? ' crop-area--safe' : ''}` }}
            onCropChange={setCrop} onZoomChange={setZoom}
            onCropComplete={(_percent, pixels) => setArea(pixels)} />
        )}
      </div>
      <label className="crop-zoom">
        <span>縮放</span>
        <input type="range" min={MIN_ZOOM} max={MAX_ZOOM} step={0.01} value={zoom}
          onChange={(event) => setZoom(Number(event.target.value))} aria-label="縮放" />
      </label>
      {error && <p className="field__error" role="alert">{error}</p>}
      <div className="dialog__actions">
        <button type="button" className="btn" onClick={onCancel} disabled={busy}>取消</button>
        <button type="button" className="btn btn--primary" onClick={() => void confirm()} disabled={busy || !area}>
          {busy ? '處理中…' : '確定裁切'}
        </button>
      </div>
    </Dialog>
  )
}
