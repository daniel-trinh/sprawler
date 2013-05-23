package biz

import biz.config.CrawlerConfig
import org.scalatest._
import biz.http.client.RobotRules

class RobotRulesSpec extends WordSpec with ShouldMatchers {
  "RobotRules" when {
    def createDummyRule(robotsTxtContext: String) = {
      RobotRules.create(
        domain = "https://somepage.com",
        crawlerName = "someName",
        robotsTxtContent = robotsTxtContext
      )
    }
    ".getCrawlDelay" should {
      "handle normal delay" in {
        val ruleTxt =
          """
            |User-agent: *
            |Crawl-delay: 10
          """.stripMargin

        val rule = createDummyRule(ruleTxt)
        rule.getCrawlDelay should be === (10 * 1000)
      }
      "handle invalid negative delay" in {
        val ruleWithInvalidDelayTxt =
          """
            |User-agent: *
            |Crawl-delay: -12
          """.stripMargin

        val ruleWithInvalidDelay = createDummyRule(ruleWithInvalidDelayTxt)
        ruleWithInvalidDelay.getCrawlDelay should be === CrawlerConfig.defaultCrawlDelay
      }
      "handle nonexistant delay" in {
        val ruleWithNoDelayTxt = "User-agent: *"

        val ruleWithNoDelay = createDummyRule(ruleWithNoDelayTxt)

        ruleWithNoDelay.getCrawlDelay should be === CrawlerConfig.defaultCrawlDelay
      }
      "enormous delays should not allow any URLs" in {
        val ruleWithEnormousDelayTxt =
          """
            |User-agent: *
            |Crawl-delay: 3333333
          """.stripMargin

        val ruleWithEnormousDelay = createDummyRule(ruleWithEnormousDelayTxt)
        ruleWithEnormousDelay.isAllowNone should be === true
      }
      "handle malformed txt" in {
        val malformedDelayTxt =
          """
            |User-agent: *
            |Crawl-delay: NaN
          """.stripMargin

        val ruleWithMalformedDelay = createDummyRule(malformedDelayTxt)
        ruleWithMalformedDelay.getCrawlDelay should be === CrawlerConfig.defaultCrawlDelay
      }
    }
  }
}
