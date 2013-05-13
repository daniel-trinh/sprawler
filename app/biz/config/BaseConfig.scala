package biz

import com.typesafe.config.ConfigFactory

trait BaseConfig {
  lazy val baseConfig = ConfigFactory.load()
}
