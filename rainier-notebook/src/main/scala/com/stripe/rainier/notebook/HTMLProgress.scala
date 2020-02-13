package com.stripe.rainier.notebook

import com.stripe.rainier.sampler._
import almond.api.JupyterApi

case class HTMLProgress(kernel: JupyterApi, delay: Double) extends Progress {
  def init(n: Int) = {
    val id = java.util.UUID.randomUUID().toString
    1.to(n).toList.map { i =>
      val idN = id + "-" + i
      kernel.publish.html("Starting", idN)
      ProgressState(delay, { p =>
        render(p, idN)
      })
    }
  }

  def render(p: ProgressState, id: String): Unit = {
    kernel.publish.updateHtml("Going", id)
  }
}
