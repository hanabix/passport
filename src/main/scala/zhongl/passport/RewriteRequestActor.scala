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

import akka.actor.Status._
import akka.actor.{Actor, ActorLogging, Props, Stash}
import akka.http.scaladsl.model.HttpRequest
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import zhongl.passport.Rewrite._

final class RewriteRequestActor private (source: Source[Docker.Mode.Rules, Any], mayBeRequest: Option[Request])
    extends Actor
    with Stash
    with ActorLogging {

  import RewriteRequestActor._

  implicit private val mat = Materializer(context)

  source
    .map(locate)
    .map(g => mayBeRequest.map(_ & g).getOrElse(g))
    .map(Locate)
    .runWith(Sink.actorRef(self, Complete, Failure))

  override def receive: Receive = {
    case Failure(cause) => unstashAll(); context.become(dying(cause))
    case Locate(f)      => unstashAll(); context.become(rewrite(f))
    case Complete       => context.stop(self)
    case _              => stash()
  }

  private def dying(cause: Throwable): Receive = { case _: HttpRequest =>
    sender() ! Failure(cause)
    log.error(cause, "Stop actor cause by source failure")
    context.stop(self)
  }

  private def rewrite(f: Request): Receive = {
    case r: HttpRequest => sender() ! Success(f(r))
    case Locate(g)      => context.become(rewrite(g))
    case Complete       => context.stop(self)
    case Failure(cause) => context.become(dying(cause))
  }

}

object RewriteRequestActor {

  def props(source: Source[Docker.Mode.Rules, Any], mayBeRequest: Option[Request] = None): Props = {
    Props(new RewriteRequestActor(source, mayBeRequest))
  }

  def locate(rules: Docker.Mode.Rules): Request = {
    Rewrite.HostOfUri(h => rules.find(p => p._1.pattern.matcher(h.host.address()).matches()).map(p => p._2))
  }

  sealed private trait Message
  final private case class Locate(g: Rewrite.Request) extends Message
  private case object Complete                        extends Message
}
