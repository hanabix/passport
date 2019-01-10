package fun.zhongl.passport
import java.net.InetAddress

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpRequest, RemoteAddress, Uri}
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.Uri.Authority
import akka.http.scaladsl.model.headers._
import fun.zhongl.passport.Forward.{DefaultRewrite, MissingRemoteAddressException}
import fun.zhongl.passport.NetworkInterfaces._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class ForwardSpec extends WordSpec with Matchers with BeforeAndAfterAll {

  implicit val system = ActorSystem(getClass.getSimpleName)

  private val maybeAddress = findFirstNetworkInterfaceHasInet4Address.flatMap(i => localAddress(i.getName))

  "Forward" should {
    "stop recursive forward" in {
      maybeAddress.foreach { addr =>
        val f = Forward.handle.apply(HttpRequest(headers = List(`X-Forwarded-For`(addr))))
        Await.result(f, Duration.Inf).status shouldBe LoopDetected
      }
    }

    "complain missing host" in {
      Await.result(Forward.handle.apply(HttpRequest()), Duration.Inf).status shouldBe BadRequest
    }

    "add forwarded for" in {
      maybeAddress.foreach { addr =>
        val host   = Uri.Host("a.b")
        val client = "192.168.2.1"
        val req    = DefaultRewrite(Some(Authority(host)), addr, Some(client), None, List.empty)(HttpRequest(uri = "http://b.c"))

        req shouldBe HttpRequest(uri = s"http://$host", headers = List(`X-Forwarded-For`(client, addr)))
      }
    }

    "append forwarded for" in {
      maybeAddress.foreach { addr =>
        val host                  = Uri.Host("a.b")
        val client: RemoteAddress = "192.168.2.67"
        val proxy: RemoteAddress  = "192.168.2.1"
        val xForwardedFor         = `X-Forwarded-For`(client, proxy)
        val req                   = DefaultRewrite(Some(Authority(host)), addr, None, Some(xForwardedFor), List.empty)(HttpRequest(uri = "http://b.c"))
        req shouldBe HttpRequest(uri = s"http://$host", headers = List(`X-Forwarded-For`(client, proxy, addr)))
      }
    }

    "complain missing remote address header" in {
      Await.result(Forward.handle.apply(HttpRequest(headers = List(Host(Uri.Host("a.b"))))), Duration.Inf).status shouldBe InternalServerError
    }
  }

  override protected def afterAll(): Unit = system.terminate()
}
