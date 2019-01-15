package fun.zhongl.passport
import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpRequest, HttpResponse}
import akka.http.scaladsl.server.Directives
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class EchoSpec extends WordSpec with Matchers with BeforeAndAfterAll with Directives {
  implicit val system = ActorSystem(getClass.getSimpleName)

  "Echo" should {
    "handle" in {
      val html   = """
                   |<html>
                   |  <head>
                   |   <title>Who am i</title>
                   |  </head>
                   |  <body>
                   |    <h1>a.b</h1>
                   |  </body>
                   |</html>
                   |""".stripMargin
      val future = Echo.handle(extractHost).apply(HttpRequest(uri = "http://a.b"))
      Await.result(future, Duration.Inf) shouldBe HttpResponse(entity = HttpEntity(ContentTypes.`text/html(UTF-8)`, html))
    }
  }

  override protected def afterAll(): Unit = system.terminate()
}
