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

import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}

final class CachedLatest[T](latest: T) extends GraphStage[FlowShape[T, T]] {
  val in  = Inlet[T]("CachedLatest.in")
  val out = Outlet[T]("CachedLatest.out")

  override val shape = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private var currentValue: T = latest

    setHandlers(in, out, new InHandler with OutHandler {
      override def onPush(): Unit = {
        currentValue = grab(in)
        pull(in)
      }

      override def onPull(): Unit = {
        push(out, currentValue)
      }
    })

    override def preStart(): Unit = {
      pull(in)
    }
  }
}
object CachedLatest {
  def apply[T](latest: T): CachedLatest[T] = new CachedLatest(latest)
}
