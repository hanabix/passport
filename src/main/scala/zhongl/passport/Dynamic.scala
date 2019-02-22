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

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Source}
import akka.util.ByteString

object Dynamic {
  val label = "passport.rule"

  val filterByLabel = Map("label" -> List(label))

  def by(docker: Docker): String => Source[List[(String, String)], NotUsed] = {
    case "docker" =>
      source(
        docker.events(Map("scope" -> List("local"), "type" -> List("container"), "event" -> List("start", "destroy"))),
        docker.containers(filterByLabel).map(_.map(c => c.`Labels`(label) -> c.`Names`.head.substring(1)))
      )
    case _ =>
      source(
        docker.events(Map("scope" -> List("swarm"), "type" -> List("service"), "event" -> List("update", "remove"))),
        docker.services(filterByLabel).map(_.map(c => c.`Spec`.`Labels`(label) -> c.`Spec`.`Name`))
      )
  }

  private def source(
      events: Source[ByteString, Any],
      get: Flow[Any, List[(String, String)], NotUsed]
  ): Source[List[(String, String)], NotUsed] = {
    Source.single(ByteString.empty).concat(events).via(get)
  }


}
