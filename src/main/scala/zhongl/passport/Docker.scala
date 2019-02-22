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

import java.io.File

import akka.NotUsed
import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.HttpEntity.Chunked
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Host
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import akka.stream.alpakka.unixdomainsocket.scaladsl.UnixDomainSocket
import akka.stream.scaladsl._
import akka.stream.{Attributes, Materializer}
import akka.util.ByteString
import spray.json._
import zhongl.passport.Docker.{Container, Service}

final class Docker(base: Uri, outgoing: () => Flow[HttpRequest, HttpResponse, _]) extends Docker.JsonSupport {

  def events(filters: Map[String, List[String]]): Source[ByteString, NotUsed] = {
    val query   = Uri.Query("filters" -> filters.toJson.compactPrint)
    val request = HttpRequest(uri = base.copy(path = Path / "events").withQuery(query))
    Source
      .single(request)
      .log("Docker events request")
      .withAttributes(Attributes.logLevels(Logging.InfoLevel))
      .via(outgoing())
      .log("Docker events response")
      .withAttributes(Attributes.logLevels(Logging.InfoLevel))
      .flatMapConcat {
        case HttpResponse(StatusCodes.OK, _, Chunked(ContentTypes.`application/json`, chunks), _) => chunks
      }
      .log("Docker events push")
      .map(_.data())
  }

  def containers[T](filters: Map[String, List[String]])(implicit mat: Materializer): Flow[T, List[Container], NotUsed] = {
    list[T, List[Container]](filters, Path / "containers" / "json")
  }

  def services[T](filters: Map[String, List[String]])(implicit mat: Materializer): Flow[T, List[Service], NotUsed] = {
    list[T, List[Service]](filters, Path / "services")
  }

  private def list[A, B](filters: Map[String, List[String]], path: Path)(implicit u: Unmarshaller[ResponseEntity, B], mat: Materializer) = {
    val query   = Uri.Query("filters" -> filters.toJson.compactPrint)
    val request = HttpRequest(uri = base.copy(path = path).withQuery(query))
    Flow[A]
      .map(_ => request)
      .log(s"Docker $path request")
      .withAttributes(Attributes.logLevels(Logging.InfoLevel))
      .via(outgoing())
      .log(s"Docker $path response")
      .withAttributes(Attributes.logLevels(Logging.InfoLevel))
      .mapAsync(1) {
        case HttpResponse(StatusCodes.OK, _, entity, _) => Unmarshal(entity).to[B]
      }
      .log(s"Docker $path unmarshal")
  }
}

/**
  *
  */
object Docker {

  def apply(host: String = fromEnv)(implicit system: ActorSystem): Docker = {
    Uri(host) match {
      case Uri("unix", _, Path(p), _, _) =>
        new Docker(Uri("http://localhost"), unixDomainSocket(new File(p)))

      case u =>
        new Docker(u.withScheme("http"), () => Forward().mapAsync(1)(identity))
    }
  }

  private def unixDomainSocket(file: File)(implicit system: ActorSystem) = { () =>
    val http      = Http().clientLayer(Host("localhost")).atop(TLSPlacebo())
    val transport = UnixDomainSocket().outgoingConnection(file).mapMaterializedValue(_ => NotUsed)
    http.join(transport)
  }

  private def fromEnv = {
    sys.env.getOrElse("DOCKER_HOST", "unix:///var/run/docker.dock")
  }

  final case class Container(`Id`: String, `Names`: List[String], `Labels`: Map[String, String])
  final case class Service(`ID`: String, `Spec`: Spec)
  final case class Spec(`Name`: String, `Labels`: Map[String, String])

  trait JsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
    implicit val specF: JsonFormat[Spec]           = jsonFormat2(Spec)
    implicit val containerF: JsonFormat[Container] = jsonFormat3(Container)
    implicit val serviceF: JsonFormat[Service]     = jsonFormat2(Service)
  }

}