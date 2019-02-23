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
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class HandlersSpec extends WordSpec with Matchers with BeforeAndAfterAll {
  implicit val system = ActorSystem(getClass.getSimpleName)
  implicit val mat    = ActorMaterializer()

  "Handlers" should {
    "create a flow with guard" in {
      val flow   = Handle(Source.repeat(List.empty))
      val future = Source.single(HttpRequest()).via(flow).runWith(Sink.head)
      Await.result(future, Duration.Inf) shouldBe HttpResponse(StatusCodes.Unauthorized)
    }
  }

  override protected def afterAll(): Unit = system.terminate()
}
