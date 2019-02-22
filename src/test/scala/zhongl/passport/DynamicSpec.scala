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

import java.nio.file.Files

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpEntity.Chunked
import akka.http.scaladsl.model.{ContentTypes, Uri}
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.ActorMaterializer
import akka.stream.alpakka.unixdomainsocket.scaladsl.UnixDomainSocket
import akka.stream.scaladsl.{Sink, Source, TLSPlacebo}
import akka.util.ByteString
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

import scala.concurrent.Await
import scala.concurrent.duration._

class DynamicSpec extends WordSpec with Matchers with BeforeAndAfterAll with Directives with Docker.JsonSupport {

  private implicit val system = ActorSystem(getClass.getSimpleName)
  private implicit val mat    = ActorMaterializer()
  private implicit val ex     = system.dispatcher

  private val file = {
    val f = Files.createTempFile("passport", "sock").toFile
    f.delete()
    f.deleteOnExit()
    f
  }

  private val bound = {
    val flow = mockDockerDaemon.join(Http().serverLayer()).join(TLSPlacebo())
    Await.result(UnixDomainSocket().bindAndHandle(flow, file), Duration.Inf)
  }

  private val docker = Docker(Uri(file.toURI.toString).withScheme("unix").toString())

  "Dynamic" should {

    "111" in {
      val f = Dynamic.by(docker).apply("docker").runForeach(println)
      Await.result(f, Duration.Inf)

    }

    "by docker local" ignore  {
      val f = Dynamic.by(docker).apply("docker").runWith(Sink.head)
      Await.result(f, Duration.Inf) shouldBe List(".+" -> "demo:8080")

    }

    "by docker swarm" ignore  {
      val f = Dynamic.by(docker).apply("swarm").runWith(Sink.head)
      Await.result(f, Duration.Inf) shouldBe List(".+" -> "demo")
    }

  }

  def mockDockerDaemon: Route = get {
    concat(
      path("events") {
        complete(Chunked.fromData(ContentTypes.`application/json`, Source.repeat(ByteString("1")).delay(1.second)))
      },
      (path("containers" / "json") & parameter("filters")) { _ =>
        complete(List(Docker.Container("id", List("/demo"), Map("passport.rule" -> ".+|>|:8080"))))
      },
      (path("services") & parameter("filters")) { _ =>
        complete(List(Docker.Service("id", Docker.Spec("demo", Map("passport.rule" -> ".+")))))
      },
      pathEndOrSingleSlash {
        complete("ok")
      }
    )
  }

  override protected def afterAll(): Unit = {
    bound.unbind()
    system.terminate()
  }
}
