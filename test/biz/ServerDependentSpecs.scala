package biz

/**
 * Created with IntelliJ IDEA.
 * User: daniel
 * Date: 8/18/13
 * Time: 12:28 AM
 * To change this template use File | Settings | File Templates.
 */

import org.scalatest._
import play.core.StaticApplication

/**
 * There can only be one!
 * Contains all specs to run with a global before / after hook, which is necessary
 * to get
 */
class ServerDependentSpecs extends Specs(
  new HttpCrawlerClientSpec,
  new RedirectFollowerSpec,
  new RobotRulesSpec,
  new ThrottlerSpec
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