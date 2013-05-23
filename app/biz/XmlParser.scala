package biz

import org.jsoup.Jsoup
import scala.collection.JavaConversions._

object XmlParser {
  /**
   * Finds all anchor href tags in a [[java.lang.String]] of html.
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
   * @return A list of links found in the string of html.
   */
  def extractLinks(response: String): List[String] = {

    val xml = Jsoup.parse(response)
    val links = xml.select("a[href]")

    links.view.map { link =>
      link.attr("href")
    }.toList
  }
}