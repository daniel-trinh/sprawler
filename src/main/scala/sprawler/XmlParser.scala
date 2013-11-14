package sprawler

import org.jsoup.Jsoup
import scala.collection.JavaConversions._

object XmlParser {
  /**
   * Finds all anchor href tags in a [[java.lang.String]] of html, and returns absolute hrefs.
   *
   * Uses Jsoup, which should handle most malformed html without throwing exceptions.
   * Found links are not checked for correctness, nor are they converted into
   * relative or absolute URLs -- they are left as found originally in the html.
   *
   * Example:
   * {{{
   *   extractLinks("""<a href="#123asdf" />""")
   *   => List("#123asdf")
   * }}}
   * @param response Html in string form.
   * @param baseUri The URL where the HTML was retrieved from. Used to resolve relative URLs to absolute URLs, that occur
   *  before the HTML declares a { @code <base href>} tag.
   * @return A list of links found in the string of html.
   */
  def extractLinks(response: String, baseUri: String): List[String] = {
    if (response != null) {
      val xml = Jsoup.parse(response, baseUri)
      val links = xml.select("a")

      links.view.map { link =>
        link.attr("abs:href")
      }.toList
    } else {
      Nil
    }
  }
}