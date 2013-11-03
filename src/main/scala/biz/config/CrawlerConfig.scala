package biz.config

import com.typesafe.config.ConfigFactory
import biz.BaseConfig
import scala.concurrent.duration._

object CrawlerConfig extends BaseConfig {
  private val config = baseConfig.getConfig("crawler")

  /**
   * Crawl delay in milliseconds
   */
  val defaultCrawlDelay = config.getInt("default-crawl-delay")
  val maxRedirects = config.getInt("max-redirects")
  val maxDepth = config.getInt("max-depth")

}

abstract trait CrawlerConfig {
  val crawlDelay: Int
  val maxRedirects: Int
  val maxDepth: Int
}

case class CustomCrawlerConfig(
  crawlDelay: Int = CrawlerConfig.defaultCrawlDelay,
  maxRedirects: Int = CrawlerConfig.maxRedirects,
  maxDepth: Int = CrawlerConfig.maxDepth) extends CrawlerConfig

case object DefaultCrawlerConfig extends CrawlerConfig {
  val crawlDelay = CrawlerConfig.defaultCrawlDelay
  val maxRedirects = CrawlerConfig.maxRedirects
  val maxDepth = CrawlerConfig.maxDepth
}