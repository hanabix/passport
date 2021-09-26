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

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpEntity.Chunked
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Host
import akka.http.scaladsl.server._
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source, TLSPlacebo}
import akka.testkit.TestKit
import akka.util.ByteString
import io.netty.channel.unix._
import org.scalatest._
import zhongl.stream.netty._
import zhongl.stream.netty.all._

import java.nio.file.Files
import scala.concurrent.Await
import scala.concurrent.duration._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpecLike

class DockerSpec
    extends TestKit(ActorSystem("docker"))
    with AsyncWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with Directives
    with Docker.JsonSupport {

  implicit private val mat = Materializer(system)
  implicit private val ex  = system.dispatcher

  private val file   = {
    val f = Files.createTempFile("passport", "sock").toFile
    f.delete()
    f.deleteOnExit()
    f
  }

  private val bound  = {
    val flow = mockDockerDaemon.join(Http().serverLayer()).join(TLSPlacebo())
    Await.result(Netty().bindAndHandle[ServerDomainSocketChannel](flow, new DomainSocketAddress(file)), Duration.Inf)
  }

  private val docker = Docker(Uri(file.toURI.toString).withScheme("unix").toString())

  "Docker" should {
    "run local mode" in {
      "docker" match {
        case Docker.Mode(local) =>
          local(docker).runWith(Sink.head).map {
            case List((r, Host(h, 8080))) =>
              r.regex shouldBe ".+"
              h.address() shouldBe "demo"
            case _                        => fail()
          }
      }
    }

    "run swarm mode" in {
      "swarm" match {
        case Docker.Mode(swarm) =>
          swarm(docker).runWith(Sink.head).map {
            case List((r, Host(h, 0))) =>
              r.regex shouldBe ".+"
              h.address() shouldBe "demo"
            case _                     => fail()
          }
      }
    }

  }

  def mockDockerDaemon: Route             = get {
    concat(
      path("events") {
        complete(Chunked.fromData(ContentTypes.`application/json`, Source.repeat(ByteString("1")).delay(1.second)))
      },
      (path("containers" / "json") & parameter("filters")) { _ =>
        complete(List(Docker.Container("id", List("/demo"), Map("passport.rule" -> ".+", "passport.port" -> "8080"))))
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
    TestKit.shutdownActorSystem(system)
  }
}
