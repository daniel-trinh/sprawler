package biz

import biz.actors.LinkActorsSpec
import org.scalatest._

/**
 * Container for holding all of the specs.
 * TODO: split this into ServerDependentSpecs when sbt 0.13 is out,
 * and @DoNotDiscover annotation works with ~ test-only.
 */
class AllSpecs extends Specs(
  new LinkActorsSpec,
  new HttpCrawlerClientSpec,
  new RobotRulesSpec,
  new ThrottlerSpec,
  new XmlParserSpec
) with BeforeAndAfterAll