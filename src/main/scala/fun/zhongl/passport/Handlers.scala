package fun.zhongl.passport
import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.scaladsl.{Flow, GraphDSL, Merge}
import akka.stream.{ActorMaterializer, FlowShape, Graph}
import zhongl.stream.oauth2.Guard

import scala.concurrent.Future

object Handlers {
  def prepend(guard: Graph[Guard.Shape, NotUsed], handle: HttpRequest => Future[HttpResponse])(
      implicit system: ActorSystem): Flow[HttpRequest, HttpResponse, NotUsed] = {
    implicit val mat = ActorMaterializer()
    implicit val ex  = system.dispatcher

    val graph = GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val g = b.add(guard)
      val m = b.add(Merge[Future[HttpResponse]](2))
      val h = b.add(Flow.fromFunction(handle))

      // format: OFF
      g.out0 ~> h ~> m
      g.out1      ~> m
      // format: ON

      FlowShape(g.in, m.out)
    }

    Flow[HttpRequest].via(graph).mapAsync(1)(identity)
  }
}
