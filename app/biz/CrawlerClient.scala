package biz

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.{ Promise, Future }
import akka.actor._
import spray.can.client.HttpClient
import spray.client.HttpConduit
import spray.io._
import spray.http._
import HttpMethods._
import HttpConduit._
import play.libs.Akka
import com.typesafe.config.{ Config, ConfigFactory }

/**
 * Creates a HTTP client scoped by hostName, for performing GET requests
 * @param hostName The base url, including protocol prefix (http:// or https://)
 */
case class CrawlerClient(hostName: String) extends Client {

  val ioBridge = IOExtension(system).ioBridge()

  def fetch(path: String): Future[HttpResponse] = {
    pipeline(HttpRequest(GET, path))
  }

  def printResponse(response: Future[HttpResponse]) {
    for (res <- response) {
      println(res.entity.asString)
    }
  }

  /**
   * Perform some GET requests in parallel and print the result.
   * @param paths A traversable of relative paths to query
   */
  def parallelGet(paths: Traversable[String]) {
    Future.traverse(paths) { path =>
      val response = fetch(path)
      printResponse(response)
      response
    }
  }

  /**
   *
   * @param path
   * @return A future with the [[spray.http.HttpResponse]]
   */
  def get(path: String): Future[HttpResponse] = {
    fetch(path)
  }
}

trait Client {
  val hostName: String

  lazy private val sslOff = ConfigFactory.parseString("spray.can.client.ssl-encryption = off")
  lazy private val sslOn = ConfigFactory.parseString("spray.can.client.ssl-encryption = on")

  val (truncatedHost, port, sslEnabled, sslSetting) = if (hostName.startsWith("https://")) {
    (hostName.replaceFirst("https://", ""), 443, true, sslOn
    )
  } else if (hostName.startsWith("http://")) {
    (hostName.replaceFirst("http://", ""), 80, false, sslOff)
  } else {
    (hostName, 80, false, sslOff)
  }

  val system = Akka.system
  private val ioBridge = IOExtension(system).ioBridge()

  // Are these obs going to cause name collisions w/ multiple Clients with the same hostName?
  val (httpClient, conduit) = createClientAndConduit
  val pipeline: HttpRequest => Future[HttpResponse] = {
    sendReceive(conduit)
  }

  private def createClientAndConduit: (ActorRef, ActorRef) = {
    val httpClient = system.actorOf(Props(new HttpClient(ioBridge, sslSetting)))
    val conduit = system.actorOf(Props(new HttpConduit(httpClient, truncatedHost, port, sslEnabled)))
    (httpClient, conduit)
  }
}