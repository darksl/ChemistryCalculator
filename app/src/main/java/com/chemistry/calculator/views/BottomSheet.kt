package com.chemistry.calculator.views

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.MotionEvent.ACTION_CANCEL
import android.view.MotionEvent.ACTION_DOWN
import android.view.MotionEvent.ACTION_MOVE
import android.view.MotionEvent.ACTION_UP
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import com.chemistry.calculator.R
import com.chemistry.calculator.extensions.contains
import com.chemistry.calculator.views.BottomSheet.State.HIDDEN
import com.chemistry.calculator.views.BottomSheet.State.SHOWN
import kotlin.math.abs

class BottomSheet @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : ViewGroup(context, attrs) {
  
  enum class State {
    SHOWN, HIDDEN
  }
  
  private var currentState: State
  
  private lateinit var mainView: View
  private lateinit var slideView: View
  
  private var touchSlop = ViewConfiguration.get(context).scaledTouchSlop
  private var maxFlingVelocity = ViewConfiguration.get(context).scaledMaximumFlingVelocity
  private var isBeingDragged = false
  private var wasDownEventInSlideView = false
  private var lastY = -1f
  private var velocityTracker: VelocityTracker? = null
  private var slideRange = 0
  
  private val slideViewAnimator = ValueAnimator().apply {
    addUpdateListener {
      val value = it.animatedValue as Int
      this@BottomSheet.slideView.top = value
    }
  }
  
  init {
    val attributes = context.theme.obtainStyledAttributes(attrs,
      R.styleable.BottomSheet, 0, 0)
    currentState = when (attributes.getInt(
      R.styleable.BottomSheet_defaultState, 0)) {
      0 -> SHOWN
      1 -> HIDDEN
      else -> throw IllegalStateException("Unknown default state")
    }
    attributes.recycle()
  }
  
  fun show() {
    currentState = SHOWN
    post {
      slideViewAnimator.duration = DEFAULT_DURATION
      slideViewAnimator.setIntValues(slideView.top, height - slideRange)
      slideViewAnimator.start()
    }
  }
  
  fun hide() {
    currentState = HIDDEN
    post {
      slideViewAnimator.duration = DEFAULT_DURATION
      slideViewAnimator.setIntValues(slideView.top, height)
      slideViewAnimator.start()
    }
  }
  
  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    val widthMode = MeasureSpec.getMode(widthMeasureSpec)
    val widthSize = MeasureSpec.getSize(widthMeasureSpec)
    val heightMode = MeasureSpec.getMode(heightMeasureSpec)
    val heightSize = MeasureSpec.getSize(heightMeasureSpec)
    performMeasureChecks(widthMode, heightMode)
    mainView = getChildAt(0)
    slideView = getChildAt(1)
    measureChildWithMargins(mainView, widthMeasureSpec, 0, heightMeasureSpec, 0)
    measureChildWithMargins(slideView, widthMeasureSpec, 0, heightMeasureSpec, 0)
    slideRange = slideView.measuredHeight
    setMeasuredDimension(widthSize, heightSize)
  }
  
  override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
    if (!changed) return
    check(childCount == 2)
    val parentLeft = l + paddingLeft
    val parentTop = t + paddingTop
    val parentBottom = b - t - paddingBottom
    for (i in 0 until childCount) {
      val child = getChildAt(i)
      val params = child.layoutParams as MarginLayoutParams
      if (child === slideView) {
        val slideViewTop = when (currentState) {
          SHOWN -> parentBottom - slideView.measuredHeight + params.topMargin
          HIDDEN -> b - t
        }
        child.layout(
          parentLeft + params.marginStart,
          slideViewTop,
          parentLeft + slideView.measuredWidth,
          parentBottom - params.bottomMargin
        )
      } else {
        check(child === mainView)
        child.layout(
          parentLeft + params.marginStart,
          parentTop + params.topMargin,
          parentLeft + mainView.measuredWidth,
          parentTop + mainView.measuredHeight
        )
      }
    }
  }
  
  override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
    when (event.action) {
      ACTION_DOWN -> {
        wasDownEventInSlideView = event in slideView
        if (wasDownEventInSlideView) {
          lastY = event.y
          initVelocityTrackerIfNeeded()
          velocityTracker!!.addMovement(event)
        } else {
          isBeingDragged = false
          recycleVelocityTracker()
        }
      }
      ACTION_MOVE -> {
        if (wasDownEventInSlideView) {
          val y = event.y.toInt()
          val yDiff = abs(lastY - y)
          if (yDiff > touchSlop) {
            velocityTracker!!.addMovement(event)
            isBeingDragged = true
            lastY = event.y
          }
        }
      }
      ACTION_UP, ACTION_CANCEL -> {
        recycleVelocityTracker()
        wasDownEventInSlideView = false
        isBeingDragged = false
      }
    }
    return isBeingDragged
  }
  
  @SuppressLint("ClickableViewAccessibility")
  override fun onTouchEvent(event: MotionEvent): Boolean {
    initVelocityTrackerIfNeeded()
    when (event.actionMasked) {
      ACTION_DOWN -> {
        if (wasDownEventInSlideView) {
          lastY = event.y
          velocityTracker!!.addMovement(event)
        }
      }
      ACTION_MOVE -> {
        if (wasDownEventInSlideView) {
          velocityTracker!!.addMovement(event)
          val distance = event.y - lastY
          var newTop = slideView.top + distance.toInt()
          val maxTop = height - slideRange
          if (newTop <= maxTop) newTop = maxTop
          slideView.top = newTop
          lastY = event.y
          invalidate()
        }
      }
      ACTION_UP -> {
        velocityTracker!!.computeCurrentVelocity(1000)
        val yVelocity = velocityTracker!!.yVelocity
        if (yVelocity / maxFlingVelocity > FLING_VELOCITY_THRESHOLD) {
          currentState = HIDDEN
          val timeInSeconds = abs((height - slideView.top) / yVelocity)
          slideViewAnimator.duration = (timeInSeconds * 1000).toLong()
          slideViewAnimator.setIntValues(slideView.top, height)
          slideViewAnimator.start()
        } else {
          val middlePoint = height - slideRange * 0.65
          val endY = if (slideView.top < middlePoint) {
            currentState = SHOWN
            height - slideRange
          } else {
            currentState = HIDDEN
            height
          }
          slideViewAnimator.setIntValues(slideView.top, endY)
          slideViewAnimator.duration = DEFAULT_DURATION
          slideViewAnimator.start()
        }
        recycleVelocityTracker()
        isBeingDragged = false
      }
    }
    return true
  }
  
  override fun generateDefaultLayoutParams(): LayoutParams {
    return MarginLayoutParams(MATCH_PARENT, MATCH_PARENT)
  }
  
  override fun generateLayoutParams(p: LayoutParams): LayoutParams {
    return MarginLayoutParams(p)
  }
  
  override fun checkLayoutParams(p: LayoutParams): Boolean {
    return p is MarginLayoutParams
  }
  
  override fun generateLayoutParams(attrs: AttributeSet): LayoutParams {
    return MarginLayoutParams(context, attrs)
  }
  
  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    slideViewAnimator.cancel()
  }
  
  private fun performMeasureChecks(widthMode: Int, heightMode: Int) {
    check(widthMode == MeasureSpec.EXACTLY || widthMode == MeasureSpec.AT_MOST) {
      "Width must have an exact value or MATCH_PARENT"
    }
    check(heightMode == MeasureSpec.EXACTLY || heightMode == MeasureSpec.AT_MOST) {
      "Height must have an exact value or MATCH_PARENT"
    }
    check(childCount == 2) { "Layout must have exactly 2 children" }
  }
  
  private fun initVelocityTrackerIfNeeded() {
    if (velocityTracker == null) {
      velocityTracker = VelocityTracker.obtain()
    }
  }
  
  private fun recycleVelocityTracker() {
    if (velocityTracker != null) {
      velocityTracker!!.recycle()
      velocityTracker = null
    }
  }
  
  companion object {
    const val DEFAULT_DURATION = 250L
    const val FLING_VELOCITY_THRESHOLD = 0.18f
  }
}
