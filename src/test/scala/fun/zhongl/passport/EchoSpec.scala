package fun.zhongl.passport
import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.`User-Agent`
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
                   |    <h2>Current User</h2>
                   |    <p>a.b</p>
                   |    <br>
                   |    <h2>GET http://a.b</h2>
                   |    <h3>User-Agent: mock</h3>
                   |  </body>
                   |</html>
                   |""".stripMargin
      val future = Echo(extractHost).apply(HttpRequest(uri = "http://a.b", headers = List(`User-Agent`("mock"))))
      Await.result(future, Duration.Inf) shouldBe HttpResponse(entity = HttpEntity(ContentTypes.`text/html(UTF-8)`, html))
    }
  }

  override protected def afterAll(): Unit = system.terminate()
}
