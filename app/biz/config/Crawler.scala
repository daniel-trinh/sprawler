package biz.config

import com.typesafe.config.ConfigFactory
import biz.BaseConfig

object Crawler extends BaseConfig {
  private val config = base.getConfig("crawler")

  val maxDepth = config.getInt("max-depth")
  val domainRestSeconds = config.getInt("url-rest-seconds")
  val numConcurrentCrawlers = config.getInt("num-concurrent-crawlers")
}
