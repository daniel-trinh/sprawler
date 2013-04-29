package biz

import org.jsoup.Jsoup
import scala.collection.JavaConversions._

object XmlParser {
  def extractLinks(response: String): List[String] = {

    val xml = Jsoup.parse(response)
    val links = xml.select("a[href]")

    links.view.map { link =>
      link.attr("href")
    }.toList
  }
}