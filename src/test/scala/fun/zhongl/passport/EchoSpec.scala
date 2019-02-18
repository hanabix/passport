package fun.zhongl.passport
import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.`User-Agent`
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpRequest, HttpResponse}
import akka.http.scaladsl.server.Directives
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class EchoSpec extends WordSpec with Matchers with BeforeAndAfterAll with Directives {
  implicit val system = ActorSystem(getClass.getSimpleName)
  implicit val mat    = ActorMaterializer()

  "Echo" should {
    "handle" in {
      val future = Source
        .single(HttpRequest(uri = "http://a.b", headers = List(`User-Agent`("mock"))))
        .via(Echo())
        .runWith(Sink.head)
      Await.result(future, Duration.Inf) shouldBe HttpResponse(
        entity = HttpEntity(ContentTypes.`application/json`, """{"body":"","headers":["User-Agent: mock"],"method":"GET","uri":"http://a.b"}""")
      )
    }
  }

  override protected def afterAll(): Unit = system.terminate()
}
