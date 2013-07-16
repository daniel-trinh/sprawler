package biz.http.client

import akka.actor.{ Terminated, Status, ActorRef }
import spray.client._
import spray.http.{ HttpResponse, HttpRequest }
import spray.util.Reply
//
//class CrawlerHttpConduit(
//  httpClient: ActorRef,
//  host: String,
//  port: Int = 80,
//  sslEnabled: Boolean = false,
//  dispatchStrategy: DispatchStrategy = DispatchStrategies.NonPipelined(),
//  settings: ConduitSettings = ConduitSettings())
//    extends HttpConduit(
//      httpClient: ActorRef,
//      host: String,
//      port: Int,
//      sslEnabled: Boolean,
//      dispatchStrategy: DispatchStrategy,
//      settings: ConduitSettings) {
//
//  override def receive = {
//    case x: HttpRequest =>
//      dispatchStrategy.dispatch(RequestContext(x, settings.MaxRetries, sender), conns)
//
//    case Reply(response: HttpResponse, (conn: Conn, ctx: RequestContext, _)) =>
//      conn.deliverResponse(ctx.request, response, ctx.sender)
//      dispatchStrategy.onStateChange(conns)
//
//    case Reply(problem, (conn: Conn, ctx: RequestContext, handle: Connection)) =>
//      val error = problem match {
//        case Status.Failure(e)            => e
//        case HttpClient.Closed(_, reason) => new RuntimeException("Connection closed, reason: "+reason)
//      }
//      if (!ctx.request.canBeRetried) conn.deliverError(ctx, error)
//      else conn.retry(ctx, handle, error).foreach(dispatchStrategy.dispatch(_, conns))
//      dispatchStrategy.onStateChange(conns)
//
//    case Reply(HttpClient.Connected(handle), conn: Conn) =>
//      conn.connected(handle)
//
//    case Reply(Status.Failure(error), conn: Conn) =>
//      conn.connectFailed(error)
//      dispatchStrategy.onStateChange(conns)
//
//    case Reply(HttpClient.Closed(handle, reason), conn: Conn) =>
//      conn.closed(handle, reason)
//      dispatchStrategy.onStateChange(conns)
//
//    case Terminated(client) if client == httpClient =>
//      context.stop(self)
//  }
//}