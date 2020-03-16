package com.stripe.rainier.log

import com.google.common.flogger.{FluentLogger, LoggerConfig}
import com.google.common.flogger.backend.Platform
import FluentLogger.Api
import java.util.logging.{Level, ConsoleHandler}

trait Logger {
  def logger: FluentLogger
  def setConsoleLevel(level: Level): Unit = {
    val config = LoggerConfig.of(logger)
    config.setUseParentHandlers(false)
    config.setLevel(level)
    val consoleHandler =
      config.getHandlers.collect { case x: ConsoleHandler => x }.headOption match {
        case Some(x) => x
        case None =>
          val h = new ConsoleHandler
          config.addHandler(h)
          h
      }
    consoleHandler.setLevel(level)
  }

  def SEVERE: Api = logger.at(Level.SEVERE)
  def WARNING: Api = logger.at(Level.WARNING)
  def INFO: Api = logger.at(Level.INFO)
  def FINE: Api = logger.at(Level.FINE)
  def FINER: Api = logger.at(Level.FINER)
  def FINEST: Api = logger.at(Level.FINEST)

  def showSevere(): Unit = setConsoleLevel(Level.SEVERE)
  def showInfo(): Unit = setConsoleLevel(Level.INFO)
  def showFine(): Unit = setConsoleLevel(Level.FINE)
  def showFiner(): Unit = setConsoleLevel(Level.FINER)
  def showFinest(): Unit = setConsoleLevel(Level.FINEST)

  protected def init: FluentLogger = {
    val cons = classOf[FluentLogger].getDeclaredConstructors.head
    cons.setAccessible(true)
    val backend = Platform.getBackend(getClass.getName.dropRight(1))
    cons.newInstance(backend).asInstanceOf[FluentLogger]
  }
}
