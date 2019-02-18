package fun.zhongl.passport

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
      val flow   = Handle(None)
      val future = Source.single(HttpRequest()).via(flow).runWith(Sink.head)
      Await.result(future, Duration.Inf) shouldBe HttpResponse(StatusCodes.Unauthorized)
    }
  }

  override protected def afterAll(): Unit = system.terminate()
}
