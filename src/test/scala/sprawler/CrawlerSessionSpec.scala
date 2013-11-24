package sprawler

import akka.actor.ActorSystem

import org.scalatest._

import sprawler.crawler._

import spray.http.Uri

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await
import scala.concurrent.duration._
import sprawler.CrawlerExceptions.UnknownException

class CrawlerSessionSpec extends WordSpec with ShouldMatchers {
  "CrawlerSession" when {
    implicit val system = ActorSystem("HelloWorld")
    ".retrieveClient" should {
      "handle absolute uris" when {
        val uri = Uri("http://localhost:8080")
        val httpsUrl = Uri("https://localhost:8080")
        "add a new client and cache by uri.authority" in {
          val session = CrawlerSession.retrieveClient(uri)
          val sessionVal = Await.result(session, 2.seconds)
          val httpsSessionVal = Await.result(CrawlerSession.retrieveClient(httpsUrl), 2.seconds)

          httpsSessionVal shouldBe sessionVal

          val otherDomain = Await.result(CrawlerSession.retrieveClient(Uri("https://localhost:1234")), 2.seconds)

          otherDomain shouldNot be(session)
        }
        "retrieve an existing client" in {
          // Initialize client store with a client
          val session = CrawlerSession.retrieveClient(uri)
          val sessionVal = Await.result(session, 2.seconds)

          // Retrieve previously stored client
          val sessionRetrieved = Await.result(CrawlerSession.retrieveClient(uri), 2.seconds)
          sessionRetrieved shouldBe sessionVal
        }
      }
      "handle relative uris" when {
        "not work" in {
          val invalidClient = CrawlerSession.retrieveClient(Uri("/hello_world"))

          intercept[UnknownException] {
            Await.result(invalidClient, 2.seconds)
          }

        }
      }
    }
  }
}
