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

import akka.NotUsed
import akka.http.scaladsl.model.StatusCodes.{BadGateway, BadRequest, InternalServerError, LoopDetected}
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.stream.scaladsl.{Flow, GraphDSL, Source, ZipWith2}
import akka.stream.{FlowShape, Graph}

import scala.collection.immutable.Seq

object Rewrite {

  type Shape = FlowShape[HttpRequest, Either[HttpResponse, HttpRequest]]

  trait Action extends (HttpRequest => Either[HttpResponse, HttpRequest]) {
    def accumulate(header: HttpHeader): (Option[HttpHeader], Action)
  }

  def apply(mayBeDynamic: Option[Source[Host => Option[Host], NotUsed]], more: Action*): Graph[Shape, NotUsed] = {
    mayBeDynamic.map(dynamicGraph(more)).getOrElse(Flow[HttpRequest].map(doRewrite(_, CompoundAction(List(HostOfUri()) ++ more))))
  }

  private def dynamicGraph(more: scala.Seq[Action]): Source[Host => Option[Host], NotUsed] => Graph[Shape, NotUsed] = { dynamic =>
    GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val source = b.add(dynamic.map(f => HostOfUri(redirect = f)).map(a => CompoundAction(List(a) ++ more)))
      val zip    = b.add(new ZipWith2[HttpRequest, Action, Either[HttpResponse, HttpRequest]](doRewrite))

      // format: OFF
      source ~> zip.in1
      // format: ON

      new FlowShape(zip.in0, zip.out)
    }
  }

  private def doRewrite(req: HttpRequest, action: Action): Either[HttpResponse, HttpRequest] = {
    req.headers
      .foldLeft(List.empty[HttpHeader] -> action) {
        case ((acc, r), h) =>
          r.accumulate(h) match {
            case (None, r0)     => acc         -> r0
            case (Some(h0), r0) => (h0 :: acc) -> r0
          }
      } match {
      case (hs, rewrite) => rewrite(req.copy(headers = hs))
    }
  }

  final case class HostOfUri(host: Option[Host] = None, redirect: Host => Option[Host] = Some(_)) extends Action {
    override def accumulate(header: HttpHeader): (Option[HttpHeader], Action) = header match {
      case h: Host => Some(h) -> HostOfUri(Some(h), redirect)
      case h       => Some(h) -> this
    }

    override def apply(req: HttpRequest): Either[HttpResponse, HttpRequest] = {
      host
        .toRight(HttpResponse(BadRequest, entity = "Missing host header"))
        .map(redirect)
        .flatMap(_.toRight(HttpResponse(BadGateway, entity = "No matched host rule")))
        .map(h => req.uri.authority.copy(host = h.host, port = h.port))
        .map(a => req.uri.copy(authority = a))
        .map(u => req.copy(uri = u))
    }
  }

  /**
    *
    */
  final case class Forwarded(
      local: RemoteAddress,
      remote: Option[RemoteAddress] = None,
      forwarded: Option[`X-Forwarded-For`] = None,
      error: Option[HttpResponse] = None
  ) extends Action {
    override def accumulate(header: HttpHeader): (Option[HttpHeader], Action) = header match {
      case DetectedLoop(addresses)   => (None, copy(error = Some(HttpResponse(LoopDetected, entity = addresses.mkString(",")))))
      case h: `X-Forwarded-For`      => (None, copy(forwarded = Some(h)))
      case `Remote-Address`(address) => (None, copy(remote = Some(address)))
      case h                         => (Some(h), this)
    }

    override def apply(req: HttpRequest): Either[HttpResponse, HttpRequest] = {
      error.toLeft(Unit).flatMap { _ =>
        forwarded
          .map(f => `X-Forwarded-For`(f.addresses :+ local))
          .orElse(remote.map(f => `X-Forwarded-For`(f, local)))
          .map(h => req.copy(headers = h +: req.headers))
          .toRight(HttpResponse(InternalServerError, entity = "Missing remote address"))
      }
    }

    object DetectedLoop {
      def unapply(arg: HttpHeader): Option[Seq[RemoteAddress]] = arg match {
        case `X-Forwarded-For`(addresses) if addresses.contains(local) => Some(addresses)
        case _                                                         => None
      }
    }

  }

  /**
    *
    */
  object IgnoreTimeoutAccess extends Action {
    override def accumulate(header: HttpHeader): (Option[HttpHeader], Action) = header match {
      case _: `Timeout-Access` => None    -> this
      case h                   => Some(h) -> this
    }

    override def apply(req: HttpRequest): Either[HttpResponse, HttpRequest] = Right(req)
  }

  final case class CompoundAction(seq: Seq[_ <: Action]) extends Action {
    override def accumulate(header: HttpHeader): (Option[HttpHeader], Action) = {
      val (hs, cs) = seq.map(_.accumulate(header)).unzip
      (hs.find(_.isEmpty).getOrElse(Some(header)), CompoundAction(cs))
    }

    override def apply(req: HttpRequest): Either[HttpResponse, HttpRequest] = seq.foldLeft[Either[HttpResponse, HttpRequest]](Right(req)) {
      case (Right(r), f) => f(r)
      case (e, _)        => e
    }
  }
}
