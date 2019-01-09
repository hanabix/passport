package fun.zhongl.passport

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.ContentTypes.`text/html(UTF-8)`
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.`Set-Cookie`
import akka.http.scaladsl.server.{Directive1, Directives, Route}
import akka.stream.scaladsl.{Flow, GraphDSL, Merge}
import akka.stream.{ActorMaterializer, FlowShape}
import com.auth0.jwt.JWT
import zhongl.stream.oauth2.FreshToken.Token
import zhongl.stream.oauth2.dingtalk.{Ding, UserInfo}
import zhongl.stream.oauth2.{Guard, JwtCookie, OAuth2}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future, Promise}

object Main extends Directives {

  def main(args: Array[String]): Unit = args match {
    case Array()           => startServer("0.0.0.0", 8080)
    case Array(host)       => startServer(host, 8080)
    case Array(host, port) => startServer(host, port.toInt)
  }

  private def startServer(host: String, port: Int) = {
    implicit val system = ActorSystem("passport")
    implicit val mat    = ActorMaterializer()
    implicit val ex     = system.dispatcher

    val terminated = Http()
      .bindAndHandle(flow(system), host, port)
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

    Await.ready(terminated, Duration.Inf)
  }

  private def flow(implicit system: ActorSystem): Flow[HttpRequest, HttpResponse, Any] = {
    implicit val mat = ActorMaterializer()
    implicit val ex  = system.dispatcher

    val conf = system.settings.config.getConfig("cookie")
    val jc   = JwtCookie(conf.getString("name"), conf.getString("domain"))

    val graph = GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val guard = b.add(Guard.graph(oauth2(jc), jc.unapply(_).isDefined))
      val merge = b.add(Merge[Future[HttpResponse]](2))
      val serve = b.add(Flow.fromFunction(Route.asyncHandler(route)))
      // format: OFF
      guard.out0 ~> serve ~> merge
      guard.out1          ~> merge
      // format: ON

      FlowShape(guard.in, merge.out)
    }

    Flow[HttpRequest].via(graph).mapAsync(Runtime.getRuntime.availableProcessors())(identity)
  }

  private def route: Route = (get & pathEndOrSingleSlash & principal) { info =>
    val html = s"""
                  |<html>
                  |  <head>
                  |   <title>${info.name}</title>
                  |  </head>
                  |  <body>
                  |    <h1>${info.name}</h1>
                  |    <h3>${info.userid}</h3>
                  |    <img src='${info.avatar}'></img>
                  |  </body>
                  |</html>
             """.stripMargin
    complete(HttpEntity(`text/html(UTF-8)`, html))
  }

  private def principal: Directive1[UserInfo] = cookie("jwt").map(p => JWT.decode(p.value)).map { j =>
    val name   = j.getClaim("name").asString()
    val dept   = j.getClaim("dept").asArray(classOf[Integer]).toSeq.map(_.intValue())
    val avatar = j.getClaim("avatar").asString()
    val active = j.getClaim("active").asBoolean()
    UserInfo(j.getSubject, name, dept, avatar, active, Seq.empty)
  }
  @inline
  private def oauth2(jc: JwtCookie)(implicit system: ActorSystem): OAuth2[_ <: Token] = Ding {
    case (info, uri) =>
      val builder = JWT
        .create()
        .withSubject(info.userid)
        .withClaim("name", info.name)
        .withClaim("avatar", info.avatar)
        .withClaim("active", info.active)
        .withArrayClaim("dept", info.department.map(Integer.valueOf).toArray)
      HttpResponse(headers = List(`Set-Cookie`(jc.generate(builder))), entity = autoRedirectPage(uri))
  }

  @inline
  private def autoRedirectPage(location: Uri): ResponseEntity = {
    HttpEntity(
      `text/html(UTF-8)`,
      s"""
         |<html>
         |  <head></head>
         |  <body>
         |    <h1><a href="$location">$location</a></h1>
         |    <script>window.location.assign("${location.toString()}")</script>
         |  </body>
         |</html>
     """.stripMargin
    )
  }

}
