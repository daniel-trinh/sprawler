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

trait HttpPoller {

  def system: ActorSystem
  def hostName: String
  def port: Int

  def fetch(path: String) = {
    pipeline(HttpRequest(method = GET, uri = s"/$path"))
  }

  def printResponse(response: Future[String]) {
    for (res <- response) {
      println(res)
    }
  }

  // Spray client boilerplate
  val ioBridge = IOExtension(system).ioBridge()
  val httpClient = system.actorOf(Props(new HttpClient(ioBridge)))

  // Register a "gateway" to a particular host (0.0.0.0:9000 in this case)
  // for HTTP requests
  val conduit = system.actorOf(
    props = Props(new HttpConduit(httpClient, hostName, port)),
    name = "http-conduit"
  )

  // Create a simple pipeline to deserialize the request body into a string
  val pipeline: HttpRequest => Future[String] = {
    sendReceive(conduit) ~> unmarshal[String]
  }

  /**
   * Perform some GET requests in parallel and print the result.
   * @param paths A traversable of relative paths to query
   */
  def pollService(paths: Traversable[String]) {
    Future.traverse(paths) { path =>
      val response = fetch(path)
      printResponse(response)
      response
    }
  }
}
