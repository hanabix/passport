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
import java.net.InetSocketAddress

import akka.NotUsed
import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.HttpEntity.{ChunkStreamPart, Chunked}
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Host
import akka.http.scaladsl.settings.ClientConnectionSettings
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import akka.http.scaladsl.{ClientTransport, Http}
import akka.stream.alpakka.unixdomainsocket.scaladsl.UnixDomainSocket
import akka.stream.scaladsl._
import akka.stream.{ActorMaterializer, Attributes, FlowShape, Graph}
import akka.util.ByteString
import spray.json._
import zhongl.passport.Docker.{Container, Service}

import scala.concurrent.Future

class Docker(base: Uri, outgoing: () => Graph[FlowShape[HttpRequest, HttpResponse], Any])(implicit system: ActorSystem) extends Docker.JsonSupport {

  private implicit val mat = ActorMaterializer()
  private implicit val ex  = system.dispatcher

  def events(filters: Map[String, List[String]]): Source[ByteString, Any] = {
    val query   = Uri.Query("filters" -> filters.toJson.compactPrint)
    val request = HttpRequest(uri = base.copy(path = Path / "events").withQuery(query))

    @inline def chunks: HttpResponse => Source[ChunkStreamPart, Any] = {
      case HttpResponse(StatusCodes.OK, _, Chunked(ContentTypes.`application/json`, chunks), _) => chunks
    }

    Source
      .single(request)
      .via(outgoing())
      .log(s"docker events")
      .withAttributes(Attributes.logLevels(Logging.WarningLevel))
      .flatMapConcat(chunks)
      .log("docker flat")
      .withAttributes(Attributes.logLevels(Logging.WarningLevel))
      .map(_.data())
  }

  def containers(filters: Map[String, List[String]]): Flow[Any, List[Container], NotUsed] = {
    list[List[Container]](filters, Path / "containers" / "json")
  }

  def services(filters: Map[String, List[String]]): Flow[Any, List[Service], NotUsed] = {
    list[List[Service]](filters, Path / "services")
  }

  private def list[T](filters: Map[String, List[String]], path: Path)(implicit u: Unmarshaller[ResponseEntity, T]) = {
    val query   = Uri.Query("filters" -> filters.toJson.compactPrint)
    val request = HttpRequest(uri = base.copy(path = path).withQuery(query))

    @inline def unmarshal: HttpResponse => Future[T] = {
      case HttpResponse(StatusCodes.OK, _, entity, _) => Unmarshal(entity).to[T]
    }

    val get = Source
      .single(request)
      .via(outgoing())
      .log(s"docker $path")
      .withAttributes(Attributes.logLevels(Logging.InfoLevel))
      .mapAsync(1)(unmarshal)
      .log(s"docker $path")
    Flow.fromSinkAndSource(Sink.ignore, get)
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
        val bidi = Http().clientLayer(Host("localhost")).atop(TLSPlacebo())
        // TODO the same outgoing connection (flow) could cause materialization twice.
        new Docker(Uri("http://localhost"), () => { bidi.join(UnixDomainSocket().outgoingConnection(file)) })
      case u =>
        val forward = Forward().mapAsync(1)(identity)
        new Docker(u.copy(scheme = "http"), () => forward)
    }
  }

  private def fromEnv = {
    sys.env.getOrElse("DOCKER_HOST", "unix:///var/run/docker.dock")
  }

  final case class Container(`Id`: String, `Names`: List[String], `Labels`: Map[String, String])
  final case class Service(`ID`: String, `Spec`: Spec)
  final case class Spec(`Name`: String, `Labels`: Map[String, String])

  final class UnixSocketTransport(file: File) extends ClientTransport {
    override def connectTo(
        host: String,
        port: Int,
        settings: ClientConnectionSettings
    )(implicit system: ActorSystem): Flow[ByteString, ByteString, Future[Http.OutgoingConnection]] = {

      implicit val ex = system.dispatcher
      val address     = InetSocketAddress.createUnresolved(host, port)

      system.log.error(new Exception(), "connect")

      UnixDomainSocket()
        .outgoingConnection(file)
        .mapMaterializedValue(_.map { _ =>
          system.log.error(new Exception(), "materialize")
          Http.OutgoingConnection(address, address)
        })
    }
  }

  trait JsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
    implicit val specF: JsonFormat[Spec]           = jsonFormat2(Spec)
    implicit val containerF: JsonFormat[Container] = jsonFormat3(Container)
    implicit val serviceF: JsonFormat[Service]     = jsonFormat2(Service)
  }

}
