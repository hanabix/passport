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
import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.`Timeout-Access`
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.util.FastFuture
import akka.stream.FlowShape
import akka.stream.scaladsl.{Flow, GraphDSL, Merge, Source}
import akka.util.Timeout
import zhongl.stream.oauth2.Guard

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try
import scala.util.control.NonFatal

object Handle {

  def apply(dynamic: Source[Docker.Mode.Rules, Any])(implicit system: ActorSystem): Flow[HttpRequest, HttpResponse, NotUsed] = {
    val graph = GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val guard   = b.add(guardShape())
      val merge   = b.add(Merge[Future[HttpResponse]](3))
      val rewrite = b.add(rewriteShape(dynamic))
      val fork    = b.add(EitherFork[HttpResponse, HttpRequest]())
      val future  = b.add(Flow[HttpResponse].map(FastFuture.successful))
      val forward = b.add(Forward())

      // format: OFF
      guard.out0 ~> rewrite ~> fork.in
                               fork.out0 ~> future  ~> merge
                               fork.out1 ~> forward ~> merge
      guard.out1                                    ~> merge
      // format: ON

      FlowShape(guard.in, merge.out)
    }

    Flow[HttpRequest].via(graph).mapAsync(1)(identity).recover { case NonFatal(cause) =>
      HttpResponse(StatusCodes.InternalServerError, entity = cause.toString)
    }
  }

  private def guardShape()(implicit system: ActorSystem)                                                                     = {
    val config = system.settings.config
    val jc     = JwtCookie.apply(config)
    val ignore = jc.unapply(_: HttpRequest).isDefined
    val plugin = Platforms.bound(config)

    Guard.graph(plugin.oauth2(jc.generate), ignore)(system.dispatcher)
  }

  private def rewriteShape(dynamic: Source[Docker.Mode.Rules, Any])(implicit system: ActorSystem)                            = {
    import Rewrite._

    implicit val timeout = Timeout(2.seconds)

    val local = Try { system.settings.config.getString("interface") }.toOption
      .orElse(NetworkInterfaces.findFirstNetworkInterfaceHasInet4Address.map(_.getName))
      .flatMap(NetworkInterfaces.localAddress)
      .getOrElse(throw new IllegalStateException("Unavailable local address"))

    val base = IgnoreHeader(_.isInstanceOf[`Timeout-Access`]) & XForwardedFor(local)

    val ref = system.actorOf(RewriteRequestActor.props(dynamic, Some(base)), "RewriteRequest")
    Flow[HttpRequest].ask[Either[HttpResponse, HttpRequest]](ref)
  }
}
