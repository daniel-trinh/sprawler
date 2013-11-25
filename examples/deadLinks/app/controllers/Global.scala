import play.api.mvc._
import play.api.mvc.Results._
import scala.concurrent.Future

object Global extends play.api.GlobalSettings {
  override def onHandlerNotFound(request: RequestHeader) = {
    Future.successful(NotFound(s"""Not Found @ ${request.toString}
    |""".stripMargin))
  }
  override def onBadRequest(request: RequestHeader, error: String) = {
    Future.successful(BadRequest(
      s"""|Bad Request @ ${request.toString}
          |Error @ $error
          |""".stripMargin))
  }
}