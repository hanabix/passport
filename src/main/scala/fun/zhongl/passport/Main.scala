package fun.zhongl.passport

import java.util.concurrent.TimeUnit

import akka.NotUsed
import akka.actor.{ActorSystem, Terminated}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives
import akka.stream.scaladsl.{Flow, GraphDSL, Merge}
import akka.stream.{ActorMaterializer, FlowShape, Graph}
import com.auth0.jwt.algorithms.Algorithm
import com.typesafe.config.Config
import scopt.OptionParser
import zhongl.stream.oauth2.{Guard, JwtCookie}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future, Promise}
import scala.util.Try

object Main extends Directives {

  case class Opt(host: String = "0.0.0.0", port: Int = 8080, echo: Boolean = false)

  val parser = new OptionParser[Opt]("passport") {
    head("passport", "0.0.1")

    opt[String]('h', "host").action((x, c) => c.copy(host = x)).text("listened host address, default is 0.0.0.0")
    opt[Int]('p', "port").action((x, c) => c.copy(port = x)).text("listened port, default is 8080")
    opt[Unit]('e', "echo").action((_, c) => c.copy(echo = true)).text("enable echo mode for debug, default is disable")

    help("help").text("print this usage")
  }

  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem("passport")
    val maybeOpt        = parser.parse(args, Opt())

    run(maybeOpt)
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

    val jc     = jwtCookie(system.settings.config)
    val ignore = jc.unapply(_: HttpRequest).isDefined
    val plugin = Platforms.bound(system.settings.config)
    val guard  = Guard.graph(plugin.oauth2(jc.generate), ignore)

    maybeOpt map {
      case Opt(host, port, true) =>
        (host, port, Echo.handle(plugin.userInfoFromCookie(jc.name)))
      case Opt(host, port, _) =>
        (host, port, Forward.handle)
    } map {
      case (host, port, handle) => bind(flow(guard, handle), host, port)
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

  private def flow(guard: Graph[Guard.Shape, NotUsed], handle: HttpRequest => Future[HttpResponse])(
      implicit system: ActorSystem): Flow[HttpRequest, HttpResponse, NotUsed] = {
    implicit val mat = ActorMaterializer()
    implicit val ex  = system.dispatcher

    val graph = GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val g = b.add(guard)
      val m = b.add(Merge[Future[HttpResponse]](2))
      val h = b.add(Flow.fromFunction(handle))

      // format: OFF
      g.out0 ~> h ~> m
      g.out1      ~> m
      // format: ON

      FlowShape(g.in, m.out)
    }

    Flow[HttpRequest].via(graph).mapAsync(1)(identity)
  }

  @inline
  private def jwtCookie(conf: Config) = {
    val unit      = TimeUnit.DAYS
    val days      = conf.getDuration("cookie.expires_in", unit)
    val algorithm = Algorithm.HMAC256(conf.getString("cookie.secret"))
    JwtCookie(conf.getString("cookie.name"), conf.getString("cookie.domain"), algorithm, FiniteDuration(days, unit))
  }
}
