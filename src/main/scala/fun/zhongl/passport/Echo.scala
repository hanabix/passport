package fun.zhongl.passport

import akka.actor.ActorSystem
import akka.http.scaladsl.model.ContentTypes.`text/html(UTF-8)`
import akka.http.scaladsl.model.{HttpEntity, HttpRequest, HttpResponse}
import akka.http.scaladsl.server.{Directive1, Directives, Route}
import akka.stream.ActorMaterializer

import scala.concurrent.Future

object Echo extends Directives {

  def handle[T](principal: Directive1[T])(implicit sys: ActorSystem): HttpRequest => Future[HttpResponse] = {

    implicit val mat = ActorMaterializer()

    Route.asyncHandler((get & principal) { info =>
      val html = s"""
                  |<html>
                  |  <head>
                  |   <title>Who am i</title>
                  |  </head>
                  |  <body>
                  |    <h1>${info}</h1>
                  |  </body>
                  |</html>
             """.stripMargin
      complete(HttpEntity(`text/html(UTF-8)`, html))
    })
  }

}
