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

import akka.actor.ActorSystem
import akka.http.scaladsl.model.ContentTypes.`text/html(UTF-8)`
import akka.http.scaladsl.model.{HttpEntity, HttpRequest, HttpResponse}
import akka.http.scaladsl.server.{Directive1, Directives, Route}
import akka.stream.ActorMaterializer

import scala.concurrent.Future

object Echo extends Directives {

  def apply[T](principal: Directive1[T])(implicit sys: ActorSystem): HttpRequest => Future[HttpResponse] = {

    implicit val mat = ActorMaterializer()

    Route.asyncHandler((principal & extractRequest) { (info, req) =>
      val html = s"""
                  |<html>
                  |  <head>
                  |   <title>Who am i</title>
                  |  </head>
                  |  <body>
                  |    <h2>Current User</h2>
                  |    <p>$info</p>
                  |    <br>
                  |    <h2>${req.method.value} ${req.uri}</h2>
                  |    ${req.headers.map(h => s"<h3>${h.name()}: ${h.value()}</h3>").mkString("\n")}
                  |  </body>
                  |</html>
                  |""".stripMargin
      complete(HttpEntity(`text/html(UTF-8)`, html))
    })
  }

}
