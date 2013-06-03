package biz.config

import com.typesafe.config.ConfigFactory
import biz.BaseConfig

object CrawlerConfig extends BaseConfig {
  private val config = baseConfig.getConfig("crawler")

  val maxDepth = config.getInt("max-depth")
  /**
   * Crawl delay in milliseconds
   */
  val defaultCrawlDelay = config.getInt("default-crawl-delay")

  val maxRedirects = config.getInt("max-redirects")

}
