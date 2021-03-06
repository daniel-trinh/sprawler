package sprawler

import org.scalatest._
import sprawler.HtmlParser._

class HtmlParserSpec extends WordSpec with BeforeAndAfter with Matchers {
  "HtmlParser" when {

    ".extractLinks" should {
      // TODO: use fake stubbed endpoints instead of real ones
      "parse &amp; and friends" in {
        val html =
          """|<html>
            |  <div class="login-menu-pointer img-sprite">&nbsp;</div>
            |  <a href="/asdf"/>
            |</html>
          """.stripMargin
        extractLinks(html, "http://www.google.com") shouldBe List("http://www.google.com/asdf")
      }
      "parse & within tag attributes" in {
        val html =
          """|<html>
            |  <a href="http://blahblah.com/abc&nn/" />
            |</html>
          """.stripMargin
        extractLinks(html, "http://blahblah.com/") shouldBe List("http://blahblah.com/abc&nn/")
      }
      "parse &nn and &variable=" in {
        val html =
          """|<html>
            |  <a href="def&nn"/>
            |  <a href="abc&variable=1"/>
            |  <link rel="stylesheet" type="text/css" href="http://l.yimg.com/zz/combo?nn/lib/metro/g/uiplugins/lazy_image_0.0.4.css"/>
            |</html>""".stripMargin
        extractLinks(html, "http://www.yahoo.com") shouldBe List("http://www.yahoo.com/def&nn", "http://www.yahoo.com/abc&variable=1")
      }
      "parse javascript & in script tags" in {
        val html = """<html>
          |  <script>1 && 2</script>
          |</html>
        """.stripMargin
        extractLinks(html, "http://www.google.com") shouldBe Nil
      }
      "parse root relative link" in {
        val html =
          """<html>
            |  <a href="../folder"></a>
            |</html>
          """.stripMargin
        extractLinks(html, "http://www.google.com/path/") shouldBe List("http://www.google.com/folder")
      }
      "parse relative link into absolute links" in {
        val html =
          """<html>
            |  <a href="/folder"></a>
            |</html>
          """.stripMargin
        extractLinks(html, "http://www.google.com/path") shouldBe List("http://www.google.com/folder")
      }
    }
  }
}