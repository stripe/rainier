package com.stripe.rainier.compute

import com.google.common.flogger.FluentLogger
import java.util.logging.Level
import Level._

abstract class Logger {
  def logger: FluentLogger
  def setLevel(level: Level) = 
    LoggerConfig.of(logger).setLevel(level)

  def SEVERE = logger.as(SEVERE)
  def WARNING = logger.as(WARNING)
  def INFO = logger.as(INFO)
  def FINE = logger.as(FINE)
  def FINER = logger.as(FINER)
  def FINEST = logger.as(FINEST)
}
