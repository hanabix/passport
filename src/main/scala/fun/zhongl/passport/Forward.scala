package fun.zhongl.passport
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.Uri.Authority
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.util.FastFuture
import fun.zhongl.passport.NetworkInterfaces._

import scala.collection.immutable
import scala.concurrent.Future
import scala.util.Try
import scala.util.control.NoStackTrace

object Forward {

  def handle(implicit system: ActorSystem): HttpRequest => Future[HttpResponse] = {
    @inline
    def local =
      Try { system.settings.config.getString("forward.network.interface") }.toOption
        .orElse(findFirstNetworkInterfaceHasInet4Address.map(_.getName))
        .flatMap(localAddress)
        .getOrElse(throw new IllegalStateException("Unavailable local address"))

    val rewrite: Rewrite = DefaultRewrite(None, local, None, None, List.empty)

    req =>
      try {
        Http().singleRequest(req.headers.foldLeft[Rewrite](rewrite)((r, h) => r.update(h))(req))
      } catch {
        case LoopDetectException(addresses) => FastFuture.successful(HttpResponse(LoopDetected, entity = HttpEntity(s"$addresses")))
        case MissingHostException           => FastFuture.successful(HttpResponse(BadRequest, entity = HttpEntity(s"Missing host header")))
        case MissingRemoteAddressException  => FastFuture.successful(HttpResponse(InternalServerError, entity = HttpEntity("Missing remote address")))
        case t: Throwable                   => FastFuture.successful(HttpResponse(InternalServerError, entity = HttpEntity(t.getMessage)))
        // TODO print stacktrace and return error identity
      }
  }

  trait Rewrite {
    def update: PartialFunction[HttpHeader, Rewrite]
    def apply(request: HttpRequest): HttpRequest
  }

  final case class LoopDetectException(addresses: Seq[RemoteAddress]) extends NoStackTrace
  final case object MissingHostException                              extends NoStackTrace
  final case object MissingRemoteAddressException                     extends NoStackTrace

  final case class DefaultRewrite(authority: Option[Authority],
                                  local: RemoteAddress,
                                  from: Option[RemoteAddress],
                                  forwarded: Option[`X-Forwarded-For`],
                                  headers: immutable.Seq[HttpHeader])
      extends Rewrite {

    override def update: PartialFunction[HttpHeader, Rewrite] = {
      case h: Host                   => this.copy(authority = Some(Authority(h.host, h.port)), headers = h +: headers)
      case LoopDetected(addresses)   => throw LoopDetectException(addresses)
      case `Remote-Address`(address) => this.copy(from = Some(address))
      case h: `X-Forwarded-For`      => this.copy(forwarded = Some(h))
      case _: `Timeout-Access`       => this
      case h                         => this.copy(headers = h +: headers)
    }

    override def apply(request: HttpRequest): HttpRequest = {

      @inline
      def xForwardedFor =
        forwarded
          .map(f => `X-Forwarded-For`(f.addresses :+ local))
          .orElse(from.map(f => `X-Forwarded-For`(f, local)))
          .getOrElse(throw MissingRemoteAddressException) // suppose to set `akka.http.server.remote-address-header = on`

      authority
        .map(a => request.uri.copy(authority = a))
        .map(u => request.copy(uri = u, headers = headers :+ xForwardedFor))
        .getOrElse(throw MissingHostException)
    }

    private final object LoopDetected {
      def unapply(arg: HttpHeader): Option[Seq[RemoteAddress]] = arg match {
        case `X-Forwarded-For`(addresses) if addresses.contains(local) => Some(addresses)
        case _                                                         => None
      }
    }
  }

}
