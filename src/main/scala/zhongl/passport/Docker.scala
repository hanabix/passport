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
import akka.http.scaladsl.model.HttpEntity.{ChunkStreamPart, Chunked}
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Host
import akka.http.scaladsl.unmarshalling._
import akka.stream.scaladsl._
import akka.stream._
import akka.util.ByteString
import io.netty.channel.unix._
import spray.json._
import zhongl.passport.Docker._
import zhongl.stream.netty._
import all._

import scala.concurrent.Future
import scala.util.matching.Regex

class Docker(base: Uri, outgoing: () => Graph[FlowShape[HttpRequest, HttpResponse], Any])(implicit system: ActorSystem) extends Docker.JsonSupport {

  implicit private val mat = Materializer(system)

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
      .withAttributes(Attributes.logLevels(Logging.InfoLevel))
      .flatMapConcat(chunks)
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

    @inline def unmarshal: HttpResponse => Future[T] = { case HttpResponse(StatusCodes.OK, _, entity, _) =>
      Unmarshal(entity).to[T]
    }

    Flow[Any]
      .map(_ => request)
      .via(outgoing())
      .log(s"docker $path")
      .withAttributes(Attributes.logLevels(Logging.InfoLevel))
      .mapAsync(1)(unmarshal)
      .log(s"docker $path")
  }
}

/** */
object Docker {

  def apply(host: String = fromEnv)(implicit system: ActorSystem): Docker = {
    Uri(host) match {
      case Uri("unix", _, Path(p), _, _) =>
        val file    = new File(p)
        val bidi    = Http().clientLayer(Host("localhost")).atop(TLSPlacebo())
        val address = new DomainSocketAddress(file)
        // TODO the same outgoing connection (flow) could cause materialization twice.
        new Docker(Uri("http://localhost"), () => { bidi.join(Netty().outgoingConnection[DomainSocketChannel](address)) })
      case u                             =>
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

  trait JsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
    implicit val specF: JsonFormat[Spec]           = jsonFormat2(Spec)
    implicit val containerF: JsonFormat[Container] = jsonFormat3(Container)
    implicit val serviceF: JsonFormat[Service]     = jsonFormat2(Service)
  }

  object Mode {

    type Rules   = List[(Regex, Host)]
    type Filters = Map[String, List[String]]

    private val rule = "passport.rule"

    def unapply(arg: String): Option[Value] = arg match {
      case "docker" => Some(Local)
      case "swarm"  => Some(Swarm)
      case _        => None
    }

    sealed trait Value {
      def apply(docker: Docker): Source[Rules, NotUsed]
    }

    case object Local extends Value {
      override def apply(docker: Docker): Source[Rules, NotUsed] = {
        val filters = Map("scope" -> List("local"), "type" -> List("container"), "event" -> List("start", "destroy"))
        val update  = docker
          .containers(_: Filters)
          .map(_.map(c => c.`Labels`(rule).r -> Host(c.`Names`.head.substring(1), port(c.`Labels`))))
        source(docker.events(filters), update)
      }
    }

    case object Swarm extends Value {
      override def apply(docker: Docker): Source[Rules, NotUsed] = {
        val filters = Map("scope" -> List("swarm"), "type" -> List("service"), "event" -> List("update", "remove"))
        val update  = docker
          .services(_: Filters)
          .map(_.map(c => c.`Spec`.`Labels`(rule).r -> Host(c.`Spec`.`Name`, port(c.`Spec`.`Labels`))))
        source(docker.events(filters), update)
      }
    }

    private def port(labels: Map[String, String]): Int = {
      labels.get("passport.port").map(_.toInt).getOrElse(0)
    }

    private def source(events: Source[ByteString, Any], update: Filters => Flow[Any, Rules, NotUsed]) = {
      Source.single(ByteString.empty).concat(events).via(update(Map("label" -> List(rule))))
    }

  }

}
