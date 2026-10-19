// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { releaseStrandedEffects } from './motion'

type Fake = {
  playState: string
  effect: { target: Element; getTiming: () => { fill: string } }
  cancel: ReturnType<typeof vi.fn>
}

function fake(fill: string, playState: string, target: Element = document.body): Fake {
  return { playState, effect: { target, getTiming: () => ({ fill }) }, cancel: vi.fn() }
}

function withAnimations(list: Fake[]) {
  Object.defineProperty(document, 'getAnimations', { value: () => list, configurable: true })
}

afterEach(() => {
  Reflect.deleteProperty(document, 'getAnimations')
})

describe('releaseStrandedEffects（換頁後清掉殘留的「維持透明」動畫）', () => {
  it('已播完且 fill 為 forwards／both 的動畫會被取消', () => {
    const forwards = fake('forwards', 'finished')
    const both = fake('both', 'finished')
    withAnimations([forwards, both])
    releaseStrandedEffects()
    expect(forwards.cancel).toHaveBeenCalled()
    expect(both.cancel).toHaveBeenCalled()
  })

  it('還在播放的動畫不動（它自己會結束）', () => {
    const running = fake('forwards', 'running')
    withAnimations([running])
    releaseStrandedEffects()
    expect(running.cancel).not.toHaveBeenCalled()
  })

  it('fill 不是維持結尾的動畫（backwards／none）不動', () => {
    const backwards = fake('backwards', 'finished')
    const none = fake('none', 'finished')
    withAnimations([backwards, none])
    releaseStrandedEffects()
    expect(backwards.cancel).not.toHaveBeenCalled()
    expect(none.cancel).not.toHaveBeenCalled()
  })

  it('星星層與提示條裡的動畫不動', () => {
    const layer = document.createElement('div')
    layer.className = 'fx-layer'
    const star = document.createElement('i')
    layer.appendChild(star)
    const toast = document.createElement('div')
    toast.className = 'toast'
    document.body.append(layer, toast)

    const inLayer = fake('forwards', 'finished', star)
    const inToast = fake('forwards', 'finished', toast)
    withAnimations([inLayer, inToast])
    releaseStrandedEffects()
    expect(inLayer.cancel).not.toHaveBeenCalled()
    expect(inToast.cancel).not.toHaveBeenCalled()
    layer.remove()
    toast.remove()
  })
})
