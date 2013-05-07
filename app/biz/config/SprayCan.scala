package biz.config

import biz.BaseConfig

object SprayCan extends BaseConfig {
  private val config = base.getConfig("spray.can")

  object Client {
    private val clientConfig = config.getConfig("client")
    val userAgent = clientConfig.getString("user-agent-header")
  }
}