package biz.httpStatus

object ResponseCategories {
  sealed trait Informational1xx extends Status {
    assert(statusCode >= 100 && statusCode < 200)
    val color = "grey"
  }
  sealed trait Success2xx extends Status {
    assert(statusCode >= 200 && statusCode < 300)
    val color = "green"
  }
  sealed trait Redirect3xx extends Status {
    assert(statusCode >= 300 && statusCode < 400)
    val color = "yellow"
  }
  sealed trait ClientError4xx extends Status {
    assert(statusCode >= 400 && statusCode < 500)
    val color = "red"
  }
  sealed trait ServerError5xx extends Status {
    assert(statusCode >= 500 && statusCode < 600)
    val color = "orange"
  }
  sealed trait UnknownStatus extends Status {
    val color = "purple"
  }

  sealed trait Status {
    def statusCode: Int
    def color: String
  }
}
