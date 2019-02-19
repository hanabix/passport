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
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.HttpEntity.Chunked
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Host
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import akka.stream.Materializer
import akka.stream.alpakka.unixdomainsocket.scaladsl.UnixDomainSocket
import akka.stream.scaladsl._
import akka.util.ByteString
import spray.json._
import zhongl.passport.Docker.{Container, Service}

final class Docker(base: Uri, outgoing: () => Flow[HttpRequest, HttpResponse, _]) extends Docker.JsonSupport {

  def events(filters: Map[String, List[String]]): Source[ByteString, NotUsed] = {
    val query   = Uri.Query("filters" -> filters.toJson.compactPrint)
    val request = HttpRequest(uri = base.copy(path = Path / "events").withQuery(query))
    Source
      .single(request)
      .via(outgoing())
      .flatMapConcat {
        case HttpResponse(StatusCodes.OK, _, Chunked(ContentTypes.`application/json`, chunks), _) => chunks
      }
      .map(_.data())
  }

  def containers[T](filters: Map[String, List[String]])(implicit mat: Materializer): Flow[T, List[Container], NotUsed] = {
    list[T, List[Container]](filters, Path / "containers")
  }

  def services[T](filters: Map[String, List[String]])(implicit mat: Materializer): Flow[T, List[Service], NotUsed] = {
    list[T, List[Service]](filters, Path / "services")
  }

  private def list[A, B](filters: Map[String, List[String]], path: Path)(implicit u: Unmarshaller[ResponseEntity, B], mat: Materializer) = {
    val query   = Uri.Query("filters" -> filters.toJson.compactPrint)
    val request = HttpRequest(uri = base.copy(path = path).withQuery(query))
    Flow[A].map(_ => request).via(outgoing()).mapAsync(1) {
      case HttpResponse(StatusCodes.OK, _, entity, _) => Unmarshal(entity).to[B]
    }
  }
}

/**
  *
  */
object Docker {

  def apply(host: String = fromEnv)(implicit system: ActorSystem): Docker = {
    Uri(host) match {
      case Uri("unix", _, Path(p), _, _) =>
        val file = new File(p)
        val http = Http().clientLayer(Host("localhost")).atop(TLSPlacebo())
        val uds  = UnixDomainSocket()
        new Docker(Uri("http://localhost"), () => http.join(uds.outgoingConnection(file)))
      case u =>
        throw new IllegalStateException(s"Unsupported $host, it must be started with unix://")
    }
  }

  private def fromEnv = {
    sys.env.getOrElse("DOCKER_HOST", "unix:///var/run/docker.dock")
  }

  final case class Container(`ID`: String, `Names`: List[String], `Labels`: Map[String, String])
  final case class Service(`ID`: String, `Spec`: Spec)
  final case class Spec(`Name`: String, `Labels`: Map[String, String])

  trait JsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
    implicit val specF: JsonFormat[Spec]           = jsonFormat2(Spec)
    implicit val containerF: JsonFormat[Container] = jsonFormat3(Container)
    implicit val serviceF: JsonFormat[Service]     = jsonFormat2(Service)
  }

}
