package fun.zhongl.passport
import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.Cookie
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import zhongl.stream.oauth2.{JwtCookie, dingtalk, wechat}

class PlatformsSpec extends WordSpec with Matchers with BeforeAndAfterAll {
  implicit val system = ActorSystem(getClass.getSimpleName)

  "Platforms" should {
    "load dingtalk" in {
      Platforms.bound(ConfigFactory.parseString("dingtalk.mobile.appid = 1")) shouldBe Platforms.ding
    }

    "load wechat" in {
      Platforms.bound(ConfigFactory.parseString("wechat.corp = 1")) shouldBe Platforms.wework
    }

    "load first" in {
      Platforms.bound(ConfigFactory.parseString("dingtalk.mobile.appid = 1 \nwechat.corp = 1")) shouldBe Platforms.ding
    }

    "complain no platform has bound" in {
      intercept[IllegalStateException](Platforms.bound(ConfigFactory.empty())).getMessage shouldBe "Either [dingtalk] or [wechat] should be configured."
    }

    "have ding" in {
      val jc              = JwtCookie("n", "d")
      val info            = dingtalk.UserInfo("1", "n", "e", Seq(1), "a", true, Seq.empty)
      val signature       = Platforms.ding.builder(info).sign(jc.algorithm)
      val maybeDecodedJWT = jc.unapply(HttpRequest(headers = List(Cookie(jc.name, signature))))
      maybeDecodedJWT.map(Platforms.ding.extractor).foreach(_ shouldBe info)
    }

    "have wework" in {
      val jc              = JwtCookie("n", "d")
      val info            = wechat.UserInfo("1", "n", Seq(1), "e", "a", 0, 0, 0, "")
      val signature       = Platforms.wework.builder(info).sign(jc.algorithm)
      val maybeDecodedJWT = jc.unapply(HttpRequest(headers = List(Cookie(jc.name, signature))))
      maybeDecodedJWT.map(Platforms.wework.extractor).foreach(_ shouldBe info)
    }
  }

  override protected def afterAll(): Unit = system.terminate()
}
