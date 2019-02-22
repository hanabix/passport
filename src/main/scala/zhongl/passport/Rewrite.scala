/*
 *  Copyright 2019 Zhong Lunfu
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package zhongl.passport

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._

import scala.collection.immutable.Seq
import scala.language.implicitConversions
import scala.util.control.ControlThrowable

object Rewrite {

  type Rewrite[A, B] = A => Either[B, A]
  type Request       = Rewrite[HttpRequest, HttpResponse]
  type Headers       = Rewrite[Seq[HttpHeader], HttpResponse]

  implicit def asRequest(f: Headers): Request = r => f(r.headers).right.map(r.withHeaders)

  implicit final class RequestOps(val f: Request) extends AnyVal {
    def &(g: Request): Request = r => f(r).right.flatMap(g)
  }

  implicit final class HeadersOps(val f: Headers) extends AnyVal {
    def &(g: Request): Request = r => f(r).right.flatMap(g)
  }

  object XForwardedFor {
    def apply(local: RemoteAddress): Headers = {
      object DetectedLoop {
        def unapply(arg: HttpHeader): Option[Seq[RemoteAddress]] = arg match {
          case `X-Forwarded-For`(addresses) if addresses.contains(local) => Some(addresses)
          case _                                                         => None
        }
      }

      in =>
        try {
          in.foldLeft((List.empty[HttpHeader], Aggregated())) {
            case (_, DetectedLoop(addresses))         => throw LoopDetectedException(addresses)
            case ((hs, a), `Remote-Address`(address)) => (hs, a.copy(remote = Some(address)))
            case ((hs, a), h: `X-Forwarded-For`)      => (hs, a.copy(origin = Some(h)))
            case ((hs, a), h)                         => (h :: hs, a)
          } match {
            case (hs, a) =>
              a.origin
                .map(f => `X-Forwarded-For`(f.addresses :+ local))
                .orElse(a.remote.map(f => `X-Forwarded-For`(f, local)))
                .map(h => h :: hs)
                .map(_.reverse)
                .toRight(HttpResponse(InternalServerError, entity = "Missing remote address"))
          }
        } catch {
          case LoopDetectedException(addresses) => Left(HttpResponse(LoopDetected, entity = addresses.mkString(",")))
        }
    }

    private final case class Aggregated(origin: Option[`X-Forwarded-For`] = None, remote: Option[RemoteAddress] = None)
    private final case class LoopDetectedException(addressed: Seq[RemoteAddress]) extends ControlThrowable
  }

  object IgnoreHeader {
    def apply(f: HttpHeader => Boolean): Headers = hs => Right(hs.filterNot(f))
  }

  object HostOfUri {
    def apply(redirect: Host => Option[Host] = Some(_)): Request = { r =>
      r.headers
        .collectFirst { case h: Host => h }
        .toRight(HttpResponse(BadRequest, entity = "Missing host header"))
        .flatMap(h => redirect(h).toRight(HttpResponse(BadGateway, entity = "No matched host rule")))
        .map(h => r.uri.authority.copy(h.host, h.port))
        .map(r.uri.withAuthority)
        .map(r.withUri)
    }
  }

}
