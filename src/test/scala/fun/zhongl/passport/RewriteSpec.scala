package fun.zhongl.passport

import java.net.InetAddress

import akka.actor.ActorSystem
import akka.http.scaladsl.TimeoutAccess
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.{HttpRequest, RemoteAddress}
import fun.zhongl.passport.NetworkInterfaces._
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

class RewriteSpec extends WordSpec with Matchers with BeforeAndAfterAll with MockFactory {

  private implicit val system = ActorSystem(getClass.getSimpleName)

  private val local = RemoteAddress(InetAddress.getLoopbackAddress)

  "Rewrite" should {
    "complain missing host" in {
      intercept[Rewrite.MissingHostException.type] {
        Rewrite.HostOfUri().apply(HttpRequest())
      }
    }

    "stop recursive forward" in {
      intercept[Rewrite.LoopDetectException] {
        Rewrite.Forwarded(local).accumulate(`X-Forwarded-For`(local))
      }
    }

    "add forwarded for" in {
      val client = "192.168.2.1"

      Rewrite.Forwarded(local).accumulate(`Remote-Address`(client)) match {
        case (None, f) => f.apply(HttpRequest()).headers shouldBe List(`X-Forwarded-For`(client, local))
      }
    }

    "append forwarded for" in {
      val client = "192.168.2.67"
      val proxy  = "192.168.2.1"

      Rewrite.Forwarded(local).accumulate(`X-Forwarded-For`(client, proxy)) match {
        case (None, f) => f.apply(HttpRequest()).headers shouldBe List(`X-Forwarded-For`(client, proxy, local))
      }
    }

    "complain missing remote address header" in {
      intercept[Rewrite.MissingRemoteAddressException.type] {
        Rewrite.Forwarded(local).apply(HttpRequest())
      }
    }

    "exclude Timeout-Access header" in {
      Rewrite.IgnoreTimeoutAccess.accumulate(`Timeout-Access`(mock[TimeoutAccess])) match {
        case (None, _) =>
      }
    }

  }

  override protected def afterAll(): Unit = system.terminate()
}
