package fun.zhongl.passport

import akka.actor.ActorSystem
import akka.http.javadsl.model
import akka.http.scaladsl.TimeoutAccess
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, Uri}
import akka.japi
import fun.zhongl.passport.Forward.{DefaultRewrite, Rewrite}
import fun.zhongl.passport.NetworkInterfaces._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

import scala.concurrent.Await
import scala.concurrent.duration._

class ForwardSpec extends WordSpec with Matchers with BeforeAndAfterAll {

  implicit val system = ActorSystem(getClass.getSimpleName)

  private val maybeAddress = findFirstNetworkInterfaceHasInet4Address.flatMap(i => localAddress(i.getName))

  "Forward" should {
    "stop recursive forward" in {
      maybeAddress.foreach { addr =>
        val future = Forward.handle.apply(HttpRequest(headers = List(`X-Forwarded-For`(addr))))
        Await.result(future, Duration.Inf) shouldBe HttpResponse(LoopDetected, entity = s"Loop detected: $addr")
      }
    }

    "complain missing host" in {
      Await.result(Forward.handle.apply(HttpRequest()), Duration.Inf) shouldBe HttpResponse(BadRequest, entity = "Missing host header")
    }

    "add forwarded for" in {
      maybeAddress.foreach { addr =>
        val host   = Host(Uri.Host("a.b"))
        val client = "192.168.2.1"

        val rewrite = List(`Remote-Address`(client), host)
          .foldLeft[Rewrite](DefaultRewrite(None, addr, None, None, List.empty))(_.update(_))

        val req = rewrite(HttpRequest(uri = "http://b.c"))

        req shouldBe HttpRequest(uri = s"http://${host.host}", headers = List(host, `X-Forwarded-For`(client, addr)))
      }
    }

    "append forwarded for" in {
      maybeAddress.foreach { addr =>
        val host   = Host(Uri.Host("a.b"))
        val client = "192.168.2.67"
        val proxy  = "192.168.2.1"

        val rewrite = List(host, `X-Forwarded-For`(client, proxy))
          .foldLeft[Rewrite](DefaultRewrite(None, addr, None, None, List.empty))(_.update(_))

        val req = rewrite(HttpRequest(uri = "http://b.c"))
        req shouldBe HttpRequest(uri = s"http://${host.host}", headers = List(host, `X-Forwarded-For`(client, proxy, addr)))
      }
    }

    "complain missing remote address header" in {
      val future = Forward.handle.apply(HttpRequest(headers = List(Host(Uri.Host("a.b")))))
      Await.result(future, Duration.Inf) shouldBe HttpResponse(InternalServerError, entity = "Missing remote address")
    }

    "complain error cause" in {
      Await.result(Forward.handle.apply(null), Duration.Inf) shouldBe HttpResponse(InternalServerError, entity = "java.lang.NullPointerException")
    }

    "exclude Timeout-Access header" in {
      maybeAddress.foreach { addr =>
        val ta = new TimeoutAccess {
          override def timeout: Duration                                                                              = 1.hour
          override def updateTimeout(timeout: Duration): Unit                                                         = {}
          override def updateHandler(handler: HttpRequest => HttpResponse): Unit                                      = {}
          override def update(timeout: Duration, handler: HttpRequest => HttpResponse): Unit                          = {}
          override def updateHandler(handler: japi.Function[model.HttpRequest, model.HttpResponse]): Unit             = {}
          override def update(timeout: Duration, handler: japi.Function[model.HttpRequest, model.HttpResponse]): Unit = {}
        }
        val rewrite = DefaultRewrite(None, addr, None, None, List.empty)
        rewrite.update(`Timeout-Access`(ta)) shouldBe rewrite
      }
    }

  }

  override protected def afterAll(): Unit = system.terminate()
}
