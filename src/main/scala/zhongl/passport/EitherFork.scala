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
import akka.stream.scaladsl.{Flow, GraphDSL, Partition}
import akka.stream.{FanOutShape2, Graph}

object EitherFork {
  def apply[A, B](): Graph[FanOutShape2[Either[A, B], A, B], NotUsed] = {
    GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val partition = b.add(Partition[Either[A, B]](2, t => if (t.isLeft) 0 else 1))
      val left      = b.add(Flow[Either[A, B]].map(_.swap.toOption.get))
      val right     = b.add(Flow[Either[A, B]].map(_.toOption.get))

      // format: OFF
      partition.out(0) ~> left
      partition.out(1) ~> right
      // format: ON

      new FanOutShape2(partition.in, left.out, right.out)
    }
  }
}
