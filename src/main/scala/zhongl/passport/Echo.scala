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
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.{Host, `Timeout-Access`}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.scaladsl.{Flow, TLSPlacebo}
import akka.util.ByteString

object Echo extends {

  def apply()(implicit sys: ActorSystem): Flow[HttpRequest, HttpResponse, NotUsed] = {
    import Rewrite._
    val echo = Flow[ByteString].map { bs =>
      ByteString(s"HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: ${bs.size}\r\n\r\n") ++ bs
    }
    Flow[HttpRequest]
      .map(IgnoreHeader(_.isInstanceOf[`Timeout-Access`]))
      .map(_.right.get)
      .via(Http().clientLayer(Host("echo")).atop(TLSPlacebo()).join(echo))
  }

}
