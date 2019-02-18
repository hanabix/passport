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
import java.io.File
import java.net.InetSocketAddress

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.HttpEntity.Chunked
import akka.http.scaladsl.model.Uri.{Authority, Path}
import akka.http.scaladsl.model._
import akka.http.scaladsl.settings.{ClientConnectionSettings, ConnectionPoolSettings}
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import akka.http.scaladsl.{ClientTransport, Http}
import akka.stream.ActorMaterializer
import akka.stream.alpakka.unixdomainsocket.scaladsl.UnixDomainSocket
import akka.stream.scaladsl._
import akka.util.ByteString
import fun.zhongl.passport.Docker.{Container, Service}
import spray.json._

import scala.concurrent.Future

class Docker(base: Uri, settings: ConnectionPoolSettings)(implicit system: ActorSystem) extends Docker.JsonSupport {

  private implicit val mat = ActorMaterializer()

  def events(filters: Map[String, List[String]]): Source[ByteString, NotUsed] = {
    val query   = Uri.Query("filters" -> filters.toJson.compactPrint)
    val request = HttpRequest(uri = base.copy(path = Path / "events").withQuery(query))
    val future  = Http().singleRequest(request, settings = settings)
    Source
      .fromFuture(future)
      .flatMapConcat {
        case HttpResponse(StatusCodes.OK, _, Chunked(ContentTypes.`application/json`, chunks), _) => chunks
      }
      .map(_.data())
  }

  def containers[T](filters: Map[String, List[String]]): Flow[T, List[Container], NotUsed] = {
    list[T, List[Container]](filters, Path / "containers")
  }

  def services[T](filters: Map[String, List[String]]): Flow[T, List[Service], NotUsed] = {
    list[T, List[Service]](filters, Path / "services")
  }

  private def list[A, B](filters: Map[String, List[String]], path: Path)(implicit u: Unmarshaller[ResponseEntity, B]) = {
    val query   = Uri.Query("filters" -> filters.toJson.compactPrint)
    val request = HttpRequest(uri = base.copy(path = path).withQuery(query))
    Flow[A].mapAsync(1)(_ => Http().singleRequest(request, settings = settings)).mapAsync(1) {
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
      case u @ Uri("unix", _, Path(p), _, _) =>
        val transport = new UnixSocketTransport(new File(p))
        val settings  = ConnectionPoolSettings(system).withTransport(transport)
        new Docker(u.copy(scheme = "http", authority = Authority(Uri.Host("localhost"))), settings)
      case u =>
        new Docker(u.copy(scheme = "http"), ConnectionPoolSettings(system))
    }
  }

  private def fromEnv = {
    sys.env.getOrElse("DOCKER_HOST", "unix:///var/run/docker.dock")
  }

  final case class Container(`ID`: String, `Names`: List[String], `Labels`: Map[String, String])
  final case class Service(`ID`: String, `Spec`: Spec)
  final case class Spec(`Name`: String, `Labels`: Map[String, String])

  private final class UnixSocketTransport(file: File) extends ClientTransport {
    override def connectTo(host: String, port: Int, settings: ClientConnectionSettings)(
        implicit system: ActorSystem): Flow[ByteString, ByteString, Future[Http.OutgoingConnection]] = {
      implicit val ex = system.dispatcher

      UnixDomainSocket()
        .outgoingConnection(file)
        .mapMaterializedValue(_.map { _ =>
          val address = InetSocketAddress.createUnresolved(host, port)
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
