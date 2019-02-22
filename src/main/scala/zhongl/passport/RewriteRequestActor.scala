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

import akka.actor.Status.{Failure, Success}
import akka.actor.{Actor, ActorLogging, Props, Stash}
import akka.event.Logging
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.Host
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{ActorMaterializer, Attributes}
import zhongl.passport.Rewrite.Request

final class RewriteRequestActor private (base: Rewrite.Request, source: Source[List[(String, String)], Any])
    extends Actor
    with Stash
    with ActorLogging {

  import Rewrite._
  import RewriteRequestActor._

  private implicit val mat = ActorMaterializer()(context)
  private implicit val ex  = context.dispatcher

  source.log("update").withAttributes(Attributes.logLevels(Logging.InfoLevel)).map(locate).runWith(Sink.actorRef(self, Complete))

  override def receive: Receive = {
    case Failure(cause) => dying(cause)
    case Locate(g)      => unstashAll(); context.become(rewrite(HostOfUri(g) & base))
    case Complete       => log.info(s"init receive complete"); context.stop(self)
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

  private def locate(rules: List[(String, String)]): Host => Option[Host] = {
    val rs = rules.map { p =>
      p._1.split("\\s*\\|>\\|\\s*:", 2) match {
        case Array(r, port) => (Pattern.compile(r), Host(p._2, port.toInt))
        case Array(r)       => (Pattern.compile(r), Host(p._2, 0))
      }
    }

    host =>
      rs.find(p => p._1.matcher(host.host.address()).matches())
        .map(p => p._2)
  }

}

object RewriteRequestActor {

  def props(base: Request, source: Source[List[(String, String)], Any]): Props = Props(new RewriteRequestActor(base, source))

  private sealed trait Message
  private final case class Locate(g: Host => Option[Host]) extends Message
  private case object Complete                             extends Message
}
