import { TextureBasedEntity } from './TextureBasedEntity.js'
import { ErrorLog } from '../core/ErrorLog.js'
import { MissingImageError } from './errors/MissingImageError.js'
import { unlerp, unlerpUnclamped } from '../core/utils.js'

/* global PIXI */

export class SpriteAnimation extends TextureBasedEntity {
  constructor () {
    super()
    Object.assign(this.defaultState, {
      images: '',
      loop: false,
      duration: 1000,
      paused: false,
      started: null,
      animationProgressTime: 0,
      date: 0
    })
  }

  initData (state) {
    state.started = {
      date: state.date
    }
    state.animationProgressTime = state.date
  }

  initDisplay () {
    super.initDisplay()
    this.graphics = new PIXI.Sprite(PIXI.Texture.EMPTY)
  }

  addState (t, params, frame, frameInfo) {
    super.addState(t, params, frame)
    const toModify = this.states[frame].find(v => v.t === t)
    const date = frameInfo.date + frameInfo.frameDuration * t
    toModify.date = date
  }

  updateDisplay (state, changed, globalData, frame, progress) {
    super.updateDisplay(state, changed, globalData)

    if (state.images) {
      const duration = state.duration
      let startDate = 0
      if (state.started && state.started.date) {
        startDate = state.started.date
      }
      const images = state.images.split(',')

      const animationProgress = (state.loop ? unlerpUnclamped : unlerp)(startDate, startDate + duration, state.animationProgressTime)
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

  addAnimationProgressTime (prevState, currState) {
    currState.animationProgressTime = this.getAnimationProgressTime(prevState.paused, prevState.animationProgressTime, currState.date, prevState.date)
  }

  getAnimationProgressTime (paused, prevProgressTime, currentDate, prevDate) {
    if (paused) {
      return prevProgressTime
    } else {
      return prevProgressTime + currentDate - prevDate
    }
  }
}
