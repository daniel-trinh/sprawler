package biz

import biz.actors.DeadLinkSpec
import org.scalatest._
import play.core.StaticApplication

import play.api.test.Helpers

/**
 * Container for holding all of the specs.
 * TODO: split this into ServerDependentSpecs when sbt 0.13 is out,
 * and @DoNotDiscover annotation works with ~ test-only.
 */
class AllSpecs extends Specs(
  new HttpCrawlerClientSpec,
  new RedirectFollowerSpec,
  new RobotRulesSpec,
  new ThrottlerSpec,
  new StreamsSpec,
  new XmlParserSpec,
  new DeadLinkSpec
) with BeforeAndAfterAll with SpecHelper {

  override def beforeAll() {
    // Launch play app for Akka.system
    //    new StaticApplication(new java.io.File("."))
    startAndExecuteServer(SpecHelper.port)()

  }

  override def afterAll() {
    shutdownTestServer()
  }
}