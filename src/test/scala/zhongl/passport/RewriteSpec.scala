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

import java.net.InetAddress

import akka.actor.ActorSystem
import akka.http.scaladsl.TimeoutAccess
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.{HttpRequest, RemoteAddress}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import zhongl.passport.NetworkInterfaces._

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class RewriteSpec extends WordSpec with Matchers with BeforeAndAfterAll with MockFactory {

  private implicit val system = ActorSystem(getClass.getSimpleName)
  private implicit val mat = ActorMaterializer()

  private val local = RemoteAddress(InetAddress.getLoopbackAddress)

  "Rewrite" should {
    "do normal" in {
      val f = Source
        .single(HttpRequest(headers = List(Host("localhost"), `Remote-Address`(local))))
        .via(Rewrite(Option(Source.single(identity)), Rewrite.Forwarded(local), Rewrite.IgnoreTimeoutAccess))
        .runWith(Sink.head)
      Await.result(f, Duration.Inf) shouldBe HttpRequest(
        uri = "//localhost/", headers = List(`X-Forwarded-For`(local, local),Host("localhost"))
      )
    }

    "complain missing host" in {
      intercept[Rewrite.MissingHostException.type] {
        Rewrite.HostOfUri().apply(HttpRequest())
      }
    }

    "stop recursive forward" in {
      intercept[Rewrite.LoopDetectException] {
        Rewrite.Forwarded(local).accumulate(`X-Forwarded-For`(local))
      }
    }

    "add forwarded for" in {
      val client = "192.168.2.1"

      Rewrite.Forwarded(local).accumulate(`Remote-Address`(client)) match {
        case (None, f) => f.apply(HttpRequest()).headers shouldBe List(`X-Forwarded-For`(client, local))
      }
    }

    "append forwarded for" in {
      val client = "192.168.2.67"
      val proxy  = "192.168.2.1"

      Rewrite.Forwarded(local).accumulate(`X-Forwarded-For`(client, proxy)) match {
        case (None, f) => f.apply(HttpRequest()).headers shouldBe List(`X-Forwarded-For`(client, proxy, local))
      }
    }

    "complain missing remote address header" in {
      intercept[Rewrite.MissingRemoteAddressException.type] {
        Rewrite.Forwarded(local).apply(HttpRequest())
      }
    }

    "exclude Timeout-Access header" in {
      Rewrite.IgnoreTimeoutAccess.accumulate(`Timeout-Access`(mock[TimeoutAccess])) match {
        case (None, _) =>
      }
    }

  }

  override protected def afterAll(): Unit = system.terminate()
}
