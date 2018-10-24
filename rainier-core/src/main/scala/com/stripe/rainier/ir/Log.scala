package com.stripe.rainier.ir

import com.stripe.rainier.log._
import com.google.common.flogger.FluentLogger

object Log extends Logger {
  val logger = FluentLogger.forEnclosingClass
}
