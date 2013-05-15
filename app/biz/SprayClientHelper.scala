package biz

import akka.actor.{ Props, ActorRef }
import com.typesafe.config.ConfigFactory
import play.libs.Akka
import scala.concurrent.Future
import scala.util.Try
import spray.can.client.HttpClient
import spray.http.{ HttpResponse, HttpRequest }
import spray.io.IOExtension
import spray.client.HttpConduit

/**
 * Spray-client boilerplate remover.
 * Given a hostName, will create code for performing SSL (https) or non-SSL (http) requests.
 */
trait SprayClientHelper extends HttpClientPipelines with BaseConfig {
  def hostName: String
  def portOverride: Option[Int]

  lazy val system = Akka.system

  lazy private val ioBridge = IOExtension(system).ioBridge()

  lazy private val sslOff = ConfigFactory.parseString("spray.can.client.ssl-encryption = off")
  lazy private val sslOn = ConfigFactory.parseString("spray.can.client.ssl-encryption = on")

  lazy val (truncatedHost, port, sslEnabled, sslSetting) = if (hostName.startsWith("https://")) {
    (hostName.replaceFirst("https://", ""), 443, true, sslOn
    )
  } else if (hostName.startsWith("http://")) {
    (hostName.replaceFirst("http://", ""), 80, false, sslOff)
  } else {
    portOverride match {
      case Some(portNum) => (hostName, portNum, false, sslOff)
      case None          => (hostName, 80, false, sslOff)
    }
  }

  lazy val (httpClient, conduit) = createClientAndConduit

  lazy val pipeline: HttpRequest => Future[Try[HttpResponse]] = {
    throttledSendReceive
  }

  private def createClientAndConduit: (ActorRef, ActorRef) = {
    val httpClient = system.actorOf(Props(new HttpClient(ioBridge, sslSetting.withFallback(baseConfig))))
    val conduit = system.actorOf(Props(new HttpConduit(httpClient, truncatedHost, port, sslEnabled)))
    (httpClient, conduit)
  }
}
