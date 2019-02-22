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

import java.util.regex.Pattern

import akka.NotUsed
import akka.actor.Status.{Failure, Success}
import akka.actor.{Actor, ActorLogging, Props, Stash}
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.Host
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.ByteString
import zhongl.passport.Rewrite.Request

final class RewriteRequestActor private (base: Rewrite.Request, source: Source[List[(String, String)], Any])
    extends Actor
    with Stash
    with ActorLogging {

  import Rewrite._
  import RewriteRequestActor._

  private implicit val mat = ActorMaterializer()(context)
  private implicit val ex  = context.dispatcher

  source.map(locate).runWith(Sink.actorRef(self, Complete))

  override def receive: Receive = {
    case Failure(cause) => dying(cause)
    case Locate(g)      => unstashAll(); context.become(rewrite(HostOfUri(g) & base))
    case Complete       => context.stop(self)
    case m              => log.info(s"init receive $m"); stash()
  }

  private def dying(cause: Throwable): Receive = {
    log.warning("enter dying")

    {
      case _: HttpRequest =>
        sender() ! Failure(cause)
        log.error(cause, "Stop actor cause by source failure")
        context.stop(self)
    }
  }

  private def rewrite(f: Request): Receive = {
    log.info("enter rewrite")

    {
      case r: HttpRequest => sender() ! Success(f(r))
      case Failure(cause) => dying(cause)
      case Locate(g)      => context.become(rewrite(HostOfUri(g) & base))
    }
  }

  private def locate(rules: List[(String, String)]): Locate = {
    val rs = rules.map { p =>
      p._1.split("\\s*\\|>\\|\\s*:", 2) match {
        case Array(r, port) => (Pattern.compile(r), Host(p._2, port.toInt))
        case Array(r)       => (Pattern.compile(r), Host(p._2, 0))
      }
    }

    Locate(host => rs.find(p => p._1.matcher(host.host.address()).matches()).map(p => p._2))
  }

}

object RewriteRequestActor {

  type Filters = Map[String, List[String]]

  private val label = "passport.rule"

  def props(base: Request, docker: Docker): String => Props = {
    case "docker" =>
      val filters = Map("scope" -> List("local"), "type" -> List("container"), "event" -> List("start", "destroy"))
      val update  = docker.containers(_: Filters).map(_.map(c => c.`Labels`(label) -> c.`Names`.head.substring(1)))
      props(base, source(docker.events(filters), update))
    case _ =>
      val filters = Map("scope" -> List("swarm"), "type" -> List("service"), "event" -> List("update", "remove"))
      val update  = docker.services(_: Filters).map(_.map(c => c.`Spec`.`Labels`(label) -> c.`Spec`.`Name`))
      props(base, source(docker.events(filters), update))
  }

  def props(base: Request, source: Source[List[(String, String)], Any]): Props = {
    Props(new RewriteRequestActor(base, source))
  }

  private def source(events: Source[ByteString, Any], update: Filters => Flow[Any, List[(String, String)], NotUsed]) = {
    Source.single(ByteString.empty).concat(events).via(update(Map("label" -> List(label))))
  }

  private sealed trait Message
  private final case class Locate(g: Host => Option[Host]) extends Message
  private case object Complete                             extends Message
}
