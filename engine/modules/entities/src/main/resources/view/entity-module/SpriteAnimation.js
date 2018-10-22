import { TextureBasedEntity } from './TextureBasedEntity.js'
import { ErrorLog } from '../core/ErrorLog.js'
import { MissingImageError } from './errors/MissingImageError.js'
import { unlerp, unlerpUnclamped } from '../core/utils.js'

/* global PIXI */

export class SpriteAnimation extends TextureBasedEntity {
  constructor () {
    super()
    this.previousState = this.defaultState
    Object.assign(this.defaultState, {
      images: '',
      loop: false,
      duration: 1000,
      paused: true,
      started: null,
      animationProgress: 
    })
  }

  initDisplay () {
    super.initDisplay()
    this.graphics = new PIXI.Sprite(PIXI.Texture.EMPTY)
  }

  addState (t, params, frame) {
    super.addState(t, params, frame)
    const toModify = this.states[frame].find(v => v.t === t)
    toModify.animationProgress = this.getAnimationProgress(this.previousState.animationProgress)
    this.previousState = toModify
  }

  updateDisplay (state, changed, globalData, frame, progress) {
    super.updateDisplay(state, changed, globalData)

    if (state.images && state.started) {
      const duration = state.duration
      const date = frame.date + progress * frame.frameDuration
      const startDate = state.started.date
      const images = state.images.split(',')

      const animationProgress = (state.loop ? unlerpUnclamped : unlerp)(startDate, startDate + duration, date)
      if (animationProgress >= 0) {
        const animationIndex = Math.floor(images.length * animationProgress)
        const image = state.loop ? images[animationIndex % images.length] : (images[animationIndex] || images[images.length - 1])
        try {
          this.graphics.texture = PIXI.Texture.fromFrame(image)
        } catch (error) {
          ErrorLog.push(new MissingImageError(image, error))
        }
      }
    } else {
      this.graphics.texture = PIXI.Texture.EMPTY
    }
  }

  getAnimationProgress () {
    return 0
  }
}
