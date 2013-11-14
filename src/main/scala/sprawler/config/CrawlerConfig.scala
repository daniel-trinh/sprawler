package sprawler.config

import com.typesafe.config.ConfigFactory
import sprawler.BaseConfig
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
  val crawlDelay: FiniteDuration
  val maxRedirects: Int
  val maxDepth: Int
}

case class CustomCrawlerConfig(
  crawlDelay: FiniteDuration = CrawlerConfig.defaultCrawlDelay.milliseconds,
  maxRedirects: Int = CrawlerConfig.maxRedirects,
  maxDepth: Int = CrawlerConfig.maxDepth) extends CrawlerConfig

case object DefaultCrawlerConfig extends CrawlerConfig {
  val crawlDelay = CrawlerConfig.defaultCrawlDelay.milliseconds
  val maxRedirects = CrawlerConfig.maxRedirects
  val maxDepth = CrawlerConfig.maxDepth
}