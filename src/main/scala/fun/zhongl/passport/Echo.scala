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
import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpRequest, HttpResponse}
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow
import spray.json._

import scala.collection.immutable

object Echo extends Directives with DefaultJsonProtocol {

  type Shape = Flow[HttpRequest, HttpResponse, NotUsed]

  def apply()(implicit sys: ActorSystem): Shape = {
    implicit val mat = ActorMaterializer()
    implicit val f   = jsonFormat4(InspectedRequest)

    Route.handlerFlow((extractRequest & entity(as[String])) { (req, body) =>
      val ir = InspectedRequest(req.method.value, req.uri.toString(), req.headers.map(_.toString()), body)
      complete(HttpEntity(ContentTypes.`application/json`, ir.toJson.compactPrint))
    })
  }

  final case class InspectedRequest(method: String, uri: String, headers: immutable.Seq[String], body: String)
}
