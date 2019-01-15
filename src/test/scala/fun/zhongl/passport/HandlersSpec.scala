package fun.zhongl.passport

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.util.FastFuture
import akka.stream.scaladsl.{Flow, GraphDSL, Sink, Source}
import akka.stream.{ActorMaterializer, FanOutShape2, Graph}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import zhongl.stream.oauth2.Guard

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class HandlersSpec extends WordSpec with Matchers with BeforeAndAfterAll {
  implicit val system = ActorSystem(getClass.getSimpleName)
  implicit val mat    = ActorMaterializer()

  "Handlers" should {
    "prepend guard" in {
      val res = HttpResponse()
      val guard: Graph[Guard.Shape, NotUsed] = GraphDSL.create() { implicit b =>
        val f = b.add(Flow.fromFunction[HttpRequest, HttpRequest](identity))
        val e = b.add(Source.empty[Future[HttpResponse]])

        new FanOutShape2(f.in, f.out, e.out)
      }

      val flow   = Handlers.prepend(guard, _ => FastFuture.successful(res))
      val future = Source.single(HttpRequest()).via(flow).runWith(Sink.head)
      Await.result(future, Duration.Inf) shouldBe res
    }
  }

  override protected def afterAll(): Unit = system.terminate()
}
