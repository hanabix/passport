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
import akka.http.scaladsl.model.ContentType.WithMissingCharset
import akka.http.scaladsl.model.HttpEntity.Strict
import akka.http.scaladsl.model.headers.Host
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, MediaTypes}
import akka.http.scaladsl.server.Directives
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class EchoSpec extends WordSpec with Matchers with BeforeAndAfterAll with Directives {
  implicit val system = ActorSystem(getClass.getSimpleName)
  implicit val mat    = ActorMaterializer()

  "Echo" should {
    "handle" in {
      val future = Source
        .single(HttpRequest(uri = "http://foo.bar", headers = List(Host("foo.bar"))))
        .via(Echo())
        .runWith(Sink.head)
      Await.result(future, Duration.Inf) match {
        case HttpResponse(_, _, Strict(WithMissingCharset(MediaTypes.`text/plain`), bs), _) =>
          bs.decodeString(ByteString.UTF_8) shouldBe "GET http://foo.bar HTTP/1.1\r\nHost: foo.bar\r\nUser-Agent: akka-http/10.1.6\r\n\r\n"
      }
    }
  }

  override protected def afterAll(): Unit = system.terminate()
}
