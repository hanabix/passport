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
import akka.http.scaladsl.model.headers.Host
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Source}
import akka.util.ByteString

object Dynamic {
  val label = "passport.rule"

  val filterByLabel = Map("label" -> List(label))

  def by(docker: Docker)(implicit mat: Materializer): String => Source[Host => Host, NotUsed] = {
    case "docker" =>
      arrange(
        docker.events(Map("scope" -> List("local"), "type" -> List("container"), "event" -> List("start", "destroy"))),
        docker.containers(filterByLabel).map(_.map(c => c.`Labels`(label) -> c.`Names`.head.substring(1)))
      )

    case _ =>
      arrange(
        docker.events(Map("scope" -> List("swarm"), "type" -> List("service"), "event" -> List("update", "remove"))),
        docker.services(filterByLabel).map(_.map(c => c.`Spec`.`Labels`(label) -> c.`Spec`.`Name`))
      )
  }

  private def arrange(events: Source[ByteString, NotUsed], flow: Flow[Any, List[(String, String)], NotUsed]) = {
    Source
      .single(ByteString.empty)
      .orElse(events)
      .via(flow)
      .via(CachedLatest())
      .map(redirect)
  }

  private def redirect(rules: List[(String, String)]): Host => Host = {
    val rs = rules.map { p =>
      p._1.split("\\s*\\|>\\|\\s*:", 2) match {
        case Array(r, port) => (Pattern.compile(r), Host(p._2, port.toInt))
        case Array(r)       => (Pattern.compile(r), Host(p._2, 0))
      }
    }

    host =>
      rs.find(p => p._1.matcher(host.host.address()).matches())
        .map(p => p._2)
        .getOrElse(throw NoMatchedHostRuleException(host))
  }

  final case class NoMatchedHostRuleException(host: Host) extends Complainant {
    override def response: HttpResponse = HttpResponse(StatusCodes.BadGateway, entity = "No matched host rule")
  }

}
