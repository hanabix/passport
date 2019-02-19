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

package fun.zhongl.passport
import akka.NotUsed
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.stream.FlowShape
import akka.stream.scaladsl.{Flow, GraphDSL, Source, ZipWith2}

import scala.collection.immutable.Seq

object Rewrite {

  type Shape = Flow[HttpRequest, HttpRequest, NotUsed]

  def apply(mayBeDynamic: Option[Source[Host => Host, NotUsed]], more: Action*): Shape = {
    mayBeDynamic.map(graph(more)).getOrElse(Flow[HttpRequest].map(doRewrite(_, CompoundAction(List(HostOfUri()) ++ more))))
  }

  private def graph(more: scala.Seq[Action]): Source[Host => Host, NotUsed] => Shape = { dynamic =>
    Flow.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val source = b.add(dynamic.map(f => HostOfUri(redirect = f)).map(a => CompoundAction(List(a) ++ more)))
      val zip    = b.add(new ZipWith2[HttpRequest, Action, HttpRequest](doRewrite))

      // format: OFF
      source ~> zip.in1
      // format: ON

      FlowShape(zip.in0, zip.out)
    })
  }

  private def doRewrite(req: HttpRequest, action: Action) = {
    val (hs, rewrite) = req.headers.foldLeft((List.empty[HttpHeader], action)) {
      case ((acc, r), h) =>
        r.accumulate(h) match {
          case (None, r0)     => (acc, r0)
          case (Some(h0), r0) => (h0 :: acc, r0)
        }
    }
    rewrite(req.copy(headers = hs))
  }

  trait Action extends (HttpRequest => HttpRequest) {
    def accumulate(header: HttpHeader): (Option[HttpHeader], Action)
  }

  final case class HostOfUri(host: Option[Host] = None, redirect: Host => Host = identity) extends Action {
    override def accumulate(header: HttpHeader): (Option[HttpHeader], Action) = header match {
      case h: Host => (Some(h), HostOfUri(Some(h), redirect))
      case h       => (Some(h), this)
    }

    override def apply(req: HttpRequest): HttpRequest = {
      host
        .map(redirect)
        .map(h => req.uri.authority.copy(host = h.host, port = h.port))
        .map(a => req.uri.copy(authority = a))
        .map(u => req.copy(uri = u))
        .getOrElse(throw MissingHostException)
    }

  }

  /**
    *
    */
  final case class Forwarded(local: RemoteAddress, remote: Option[RemoteAddress] = None, forwarded: Option[`X-Forwarded-For`] = None) extends Action {
    override def accumulate(header: HttpHeader): (Option[HttpHeader], Action) = header match {
      case LoopDetected(addresses)   => throw LoopDetectException(addresses)
      case h: `X-Forwarded-For`      => (None, copy(forwarded = Some(h)))
      case `Remote-Address`(address) => (None, copy(remote = Some(address)))
      case h                         => (Some(h), this)
    }

    override def apply(req: HttpRequest): HttpRequest = {
      val h = forwarded
        .map(f => `X-Forwarded-For`(f.addresses :+ local))
        .orElse(remote.map(f => `X-Forwarded-For`(f, local)))
        .getOrElse(throw MissingRemoteAddressException)
      req.copy(headers = h +: req.headers)
    }

    object LoopDetected {
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
      case _: `Timeout-Access` => (None, this)
      case h                   => (Some(h), this)
    }

    override def apply(req: HttpRequest): HttpRequest = req
  }

  final case class CompoundAction(seq: Seq[_ <: Action]) extends Action {
    override def accumulate(header: HttpHeader): (Option[HttpHeader], Action) = {
      val (hs, cs) = seq.map(_.accumulate(header)).unzip
      (hs.find(_.isEmpty).getOrElse(Some(header)), CompoundAction(cs))
    }

    override def apply(req: HttpRequest): HttpRequest = seq.foldLeft(req)((r, f) => f(r))
  }

  final case class LoopDetectException(addresses: Seq[RemoteAddress]) extends Complainant {
    override def response: HttpResponse = HttpResponse(StatusCodes.LoopDetected, entity = addresses.mkString(","))
  }

  final case object MissingRemoteAddressException extends Complainant {
    override def response: HttpResponse = HttpResponse(StatusCodes.InternalServerError, entity = "Missing remote address")
  }

  final object MissingHostException extends Complainant {
    override def response: HttpResponse = HttpResponse(StatusCodes.BadRequest, entity = "Missing host header")
  }

}
