package biz.config

import biz.BaseConfig

object SprayCan extends BaseConfig {
  private val config = base.getConfig("spray.can")

  val userAgent = config.getString("user-agent-header")
}