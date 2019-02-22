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

import akka.http.scaladsl.TimeoutAccess
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.{HttpHeader, HttpRequest, HttpResponse, RemoteAddress}
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec}
import zhongl.passport.NetworkInterfaces._
import zhongl.passport.Rewrite._

class RewriteSpec extends WordSpec with Matchers with MockFactory {

  private val local = RemoteAddress(InetAddress.getLoopbackAddress)

  "Rewrite" should {

    "compound request with header by &" in {
      val f = HostOfUri() & IgnoreHeader(_.isInstanceOf[Host])
      f(HttpRequest(headers = List(Host("localhost")))) shouldBe Right(HttpRequest(uri = "//localhost/"))
    }

    "compound header with request by &" in {
      val f = IgnoreHeader(_ => false) & HostOfUri()
      f(HttpRequest(headers = List(Host("localhost")))) shouldBe Right(HttpRequest(uri = "//localhost/", headers = List(Host("localhost"))))
    }

    "complain missing host" in {
      HostOfUri()(HttpRequest()) shouldBe Left(HttpResponse(BadRequest, entity = "Missing host header"))
    }

    "complain no matched host rule" in {
      HostOfUri(_ => None)(HttpRequest(headers = List(Host("localhost")))) shouldBe Left(HttpResponse(BadGateway, entity = "No matched host rule"))
    }

    "stop recursive forward" in {
      XForwardedFor(local)(List(`X-Forwarded-For`(local))) shouldBe Left(HttpResponse(LoopDetected, entity = s"$local"))
    }

    "add forwarded for" in {
      val client = "192.168.2.1"
      XForwardedFor(local)(List(`Remote-Address`(client))) shouldBe Right(List(`X-Forwarded-For`(client, local)))
    }

    "append forwarded for" in {
      val client = "192.168.2.67"
      val proxy  = "192.168.2.1"

      XForwardedFor(local)(List(`X-Forwarded-For`(client, proxy))) shouldBe Right(List(`X-Forwarded-For`(client, proxy, local)))
    }

    "complain missing remote address header" in {
      XForwardedFor(local)(List(Host("localhost"))) shouldBe Left(HttpResponse(InternalServerError, entity = "Missing remote address"))
    }

    "exclude Timeout-Access header" in {
      val f = IgnoreHeader(_.isInstanceOf[`Timeout-Access`])
      f(List(`Timeout-Access`(mock[TimeoutAccess]))) shouldBe Right(List.empty[HttpHeader])
    }

  }

}
