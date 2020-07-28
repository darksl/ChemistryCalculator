package com.chemistry.calculator.utils

import com.chemistry.calculator.BuildConfig
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
inline fun assertThat(condition: Boolean, lazyMessage: () -> String = { "" }) {
  contract {
    callsInPlace(lazyMessage, InvocationKind.EXACTLY_ONCE)
    returns() implies condition
  }
  if (BuildConfig.DEBUG) {
    if (!condition) {
      throw AssertionError(lazyMessage())
    }
  }
}