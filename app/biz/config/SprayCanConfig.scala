package biz.config

import biz.BaseConfig

object SprayCanConfig extends BaseConfig {
  private val config = baseConfig.getConfig("spray.can")

  object Client {
    private val clientConfig = config.getConfig("client")
    val userAgent = clientConfig.getString("user-agent-header")
  }
}