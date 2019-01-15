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
        (host, port, Echo.handle(plugin.userInfoFromCookie(jc.name)))
      case Opt(host, port, _) =>
        (host, port, Forward.handle)
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
