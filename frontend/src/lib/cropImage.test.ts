import { describe, expect, it } from 'vitest'
import { outputSize } from './cropImage'

describe('outputSize（裁切後的固定尺寸）', () => {
  it('寬固定，高依比例四捨五入', () => {
    expect(outputSize(4 / 3, 1280)).toEqual({ width: 1280, height: 960 })
    expect(outputSize(3 / 2, 1200)).toEqual({ width: 1200, height: 800 })
  })
})
