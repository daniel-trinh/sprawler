package biz

import com.typesafe.config.ConfigFactory

trait BaseConfig {
  lazy val base = ConfigFactory.load()
}
