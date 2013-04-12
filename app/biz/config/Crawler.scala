package biz.config

import com.typesafe.config.ConfigFactory

object Crawler {
  private lazy val base = ConfigFactory.load()
  private val config = base.getConfig("crawler")

  val maxDepth = config.getInt("max-depth")
  val domainRestSeconds = config.getInt("domain-rest-seconds")
  val numConcurrentCrawlers = config.getInt("num-concurrent-crawlers")
}