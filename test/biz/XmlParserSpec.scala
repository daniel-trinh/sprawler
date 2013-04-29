import org.scalatest.{ WordSpec, FunSpec, BeforeAndAfter, BeforeAndAfterAll }
import org.scalatest.matchers.ShouldMatchers
import biz.XmlParser._

class XmlParserSpec extends WordSpec with BeforeAndAfter with ShouldMatchers {
  "XmlParser" when {

    ".extractLinks" should {
      // TODO: use fake stubbed endpoints instead of real ones
      "parse &amp; and friends" in {
        val html =
          """|<html>
            |  <div class="login-menu-pointer img-sprite">&nbsp;</div>
            |  <a href="/asdf"/>
            |</html>
          """.stripMargin
        extractLinks(html) should be === List("/asdf")
      }
      "parse & within tag attributes" in {
        val html =
          """|<html>
            |  <a href="http://blahblah.com/abc&nn/" />
            |</html>
          """.stripMargin
        extractLinks(html) should be === List("http://blahblah.com/abc&nn/")
      }
      "parse &nn and &variable=" in {
        val html =
          """|<html>
            |  <a href="def&nn"/>
            |  <a href="abc&variable=1"/>
            |  <link rel="stylesheet" type="text/css" href="http://l.yimg.com/zz/combo?nn/lib/metro/g/uiplugins/lazy_image_0.0.4.css"/>
            |</html>""".stripMargin
        extractLinks(html) should be === List("def&nn", "abc&variable=1")
      }
      "parse unicode escaped &#123" in {
      }
      "parse javascript & in script tags" in {
        val html = """<html>
          |  <script>1 && 2</script>
          |</html>
        """.stripMargin
        extractLinks(html) should be === Nil
      }
    }
  }
}