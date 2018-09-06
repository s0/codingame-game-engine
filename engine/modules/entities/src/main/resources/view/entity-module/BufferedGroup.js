import { Group } from './Group.js'
import { getRenderer, flagForDestructionOnReinit } from '../core/rendering.js'
import { WIDTH, HEIGHT } from '../core/constants.js'

/* global PIXI */

export class BufferedGroup extends Group {
  constructor () {
    super()
    Object.assign(this.defaultState, {
      alwaysRender: false
    })
    this.needsRender = true
  }
  initDisplay () {
    super.initDisplay()
    this.gameTexture = PIXI.RenderTexture.create(WIDTH, HEIGHT)
    flagForDestructionOnReinit(this.gameTexture)
    this.graphics = new PIXI.Sprite(this.gameTexture)
    this.buffer = new PIXI.Container()
  }

  updateDisplay (state, changed, globalData) {
    super.updateDisplay(state, changed, globalData)
    this.alwaysRender = state.alwaysRender
    if (changed.chidren) {
      this.needsRender = true
    }
  }

  postUpdate () {
    if (this.alwaysRender || this.needsRender) {
      getRenderer().render(this.buffer, this.gameTexture)
      this.needsRender = false
    }
  }

  get childrenContainer () {
    return this.buffer
  }
}
