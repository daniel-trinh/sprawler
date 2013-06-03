package biz.http.client

import akka.actor.{ Props, ActorRef }
import com.typesafe.config.ConfigFactory
import play.libs.Akka
import scala.concurrent.Future
import scala.util.Try
import spray.can.client.HttpClient
import spray.http.{ HttpResponse, HttpRequest }
import spray.io.IOExtension
import spray.client.HttpConduit
import biz.BaseConfig

/**
 * Spray-client boilerplate remover.
 * Given a domain, will create code for performing SSL (https) or non-SSL (http) requests.
 */
trait SprayClientHelper extends HttpClientPipelines with BaseConfig {
  def domain: String
  def portOverride: Option[Int]

  lazy val system = Akka.system

  lazy private val ioBridge = IOExtension(system).ioBridge()

  lazy private val sslOff = ConfigFactory.parseString("spray.can.client.ssl-encryption = off")
  lazy private val sslOn = ConfigFactory.parseString("spray.can.client.ssl-encryption = on")

  lazy val (truncatedHost, port, sslEnabled, sslSetting) = if (domain.startsWith("https://")) {
    (domain.replaceFirst("https://", ""), 443, true, sslOn
    )
  } else if (domain.startsWith("http://")) {
    (domain.replaceFirst("http://", ""), 80, false, sslOff)
  } else {
    portOverride match {
      case Some(portNum) => (domain, portNum, false, sslOff)
      case None          => (domain, 80, false, sslOff)
    }
  }

  lazy val (httpClient, conduit) = createClientAndConduit

  private def createClientAndConduit: (ActorRef, ActorRef) = {
    val httpClient = system.actorOf(Props(new HttpClient(ioBridge, sslSetting.withFallback(baseConfig))))
    val conduit = system.actorOf(Props(new HttpConduit(httpClient, truncatedHost, port, sslEnabled)))
    (httpClient, conduit)
  }
}
