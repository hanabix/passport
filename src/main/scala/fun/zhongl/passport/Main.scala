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
import akka.actor.{ActorSystem, Terminated}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow
import fun.zhongl.passport.CommandLine._
import zhongl.stream.oauth2.Guard

import scala.concurrent.duration._
import scala.concurrent.{Await, Future, Promise}
import scala.util.Try

object Main extends Directives {

  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem("passport")

    run(parser.parse(args, Opt()))
      .recover {
        case t: Throwable =>
          system.log.error(t, "Unexpected")
          system.terminate()
      }
      .foreach(Await.ready(_, Duration.Inf))
  }

  private def run(maybeOpt: Option[Opt])(implicit system: ActorSystem): Try[Future[Terminated]] = Try {
    implicit val mat = ActorMaterializer()
    implicit val ex  = system.dispatcher

    val jc     = JwtCookies.load(system.settings.config)
    val ignore = jc.unapply(_: HttpRequest).isDefined
    val plugin = Platforms.bound(system.settings.config)
    val guard  = Guard.graph(plugin.oauth2(jc.generate), ignore)

    maybeOpt map {
      case Opt(host, port, true) =>
        (host, port, Echo(plugin.userInfoFromCookie(jc.name)))
      case Opt(host, port, _) =>
        (host, port, Forward())
    } map {
      case (host, port, handle) => bind(Handlers.prepend(guard, handle), host, port)
    } getOrElse system.terminate()

  }

  private def bind(flow: Flow[HttpRequest, HttpResponse, NotUsed], host: String, port: Int)(implicit system: ActorSystem) = {
    implicit val mat = ActorMaterializer()
    implicit val ex  = system.dispatcher

    Http()
      .bindAndHandle(flow, host, port)
      .flatMap { bound =>
        system.log.info("Server online at {}", bound.localAddress)

        val promise = Promise[ServerBinding]()

        sys.addShutdownHook {
          promise.trySuccess(bound)
          system.log.info("Shutdown server")
        }

        promise.future
      }
      .flatMap(_.unbind())
      .flatMap(_ => system.terminate())
      .recoverWith {
        case cause: Throwable =>
          system.log.error(cause, "Force to terminate")
          system.terminate()
      }
  }

}
