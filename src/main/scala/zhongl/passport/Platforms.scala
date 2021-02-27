/*
 *  Copyright 2019 Zhong Lunfu
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package zhongl.passport

import akka.actor.ActorSystem
import akka.http.scaladsl.model.ContentTypes.`text/html(UTF-8)`
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{`Set-Cookie`, HttpCookie}
import com.auth0.jwt.interfaces.DecodedJWT
import com.auth0.jwt.{JWT, JWTCreator}
import com.typesafe.config.Config
import spray.json._
import zhongl.stream.oauth2.FreshToken.Token
import zhongl.stream.oauth2.{dingtalk, wechat, OAuth2}

object Platforms {

  type Authenticated[UserInfo] = (UserInfo, Uri) => HttpResponse
  type Builder[UserInfo]       = UserInfo => JWTCreator.Builder
  type Extractor               = DecodedJWT => String

  abstract case class Platform[UserInfo, T <: Token](builder: Builder[UserInfo], extractor: Extractor) {

    final def oauth2(f: JWTCreator.Builder => HttpCookie)(implicit system: ActorSystem): OAuth2[T] =
      concrete { case (info, uri) => ok(uri, f, builder(info)) }

    protected def concrete(authenticated: Authenticated[UserInfo])(implicit system: ActorSystem): OAuth2[T]

    private def ok(uri: Uri, f: JWTCreator.Builder => HttpCookie, builder: JWTCreator.Builder) = {
      @inline
      def autoRedirectPage(location: Uri): ResponseEntity = {
        HttpEntity(
          `text/html(UTF-8)`,
          s"""
             |<html>
             |  <head></head>
             |  <body>
             |    <h1><a href="$location">$location</a></h1>
             |    <script>window.location.assign("${location.toString()}")</script>
             |  </body>
             |</html>
             |""".stripMargin
        )
      }

      HttpResponse(StatusCodes.OK, List(`Set-Cookie`(f(builder))), autoRedirectPage(uri))
    }

  }

  val ding: Platform[dingtalk.UserInfo, dingtalk.AccessToken] = {
    val jsonSupport = new dingtalk.JsonSupport {}

    import jsonSupport._

    val extractor: Extractor = { j =>
      val name   = j.getClaim("name").asString()
      val email  = j.getClaim("email").asString()
      val dept   = j.getClaim("dept").asArray(classOf[Integer]).toSeq.map(_.intValue())
      val avatar = j.getClaim("avatar").asString()
      val active = j.getClaim("active").asBoolean()
      val roles  = j.getClaim("roles").asString().parseJson.convertTo[Seq[dingtalk.Role]]

      dingtalk.UserInfo(j.getSubject, name, email, dept, avatar, active, roles).toJson.prettyPrint
    }

    val builder: Builder[dingtalk.UserInfo] = { info =>
      JWT
        .create()
        .withSubject(info.userid)
        .withClaim("name", info.name)
        .withClaim("email", info.email)
        .withClaim("avatar", info.avatar)
        .withClaim("active", info.active)
        .withClaim("roles", info.roles.toJson.compactPrint)
        .withArrayClaim("dept", info.department.map(Integer.valueOf).toArray)
    }

    new Platform[dingtalk.UserInfo, dingtalk.AccessToken](builder, extractor) {
      def concrete(authenticated: Authenticated[dingtalk.UserInfo])(implicit system: ActorSystem) = dingtalk.Ding(authenticated)
    }

  }

  val wework: Platform[wechat.UserInfo, wechat.AccessToken] = {
    val jsonSupport = new wechat.JsonSupport {}

    import jsonSupport._

    val builder: Builder[wechat.UserInfo] = { info =>
      JWT
        .create()
        .withSubject(info.userid)
        .withClaim("name", info.name)
        .withClaim("email", info.email)
        .withClaim("avatar", info.avatar)
        .withClaim("status", Integer.valueOf(info.status))
        .withClaim("isleader", Integer.valueOf(info.isleader))
        .withClaim("enable", Integer.valueOf(info.enable))
        .withClaim("alias", info.alias)
        .withArrayClaim("dept", info.department.map(Integer.valueOf).toArray)
    }

    val extractor: Extractor = { j =>
      val name     = j.getClaim("name").asString()
      val avatar   = j.getClaim("avatar").asString()
      val dept     = j.getClaim("dept").asArray(classOf[Integer]).toSeq.map(_.intValue())
      val email    = j.getClaim("email").asString()
      val status   = j.getClaim("status").asInt().intValue()
      val isleader = j.getClaim("isleader").asInt().intValue()
      val enable   = j.getClaim("enable").asInt().intValue()
      val alias    = j.getClaim("alias").asString()
      wechat.UserInfo(j.getSubject, name, dept, email, avatar, status, isleader, enable, alias).toJson.prettyPrint
    }

    new Platform[wechat.UserInfo, wechat.AccessToken](builder, extractor) {
      override protected def concrete(authenticated: Authenticated[wechat.UserInfo])(implicit system: ActorSystem) = wechat.WeWork(authenticated)
    }
  }

  def bound(config: Config): Platform[_, _ <: Token] = {
    import scala.jdk.CollectionConverters._

    config
      .root()
      .unwrapped()
      .keySet()
      .asScala
      .collectFirst {
        case "dingtalk" => ding
        case "wechat"   => wework
      }
      .getOrElse { throw new IllegalStateException("Either [dingtalk] or [wechat] should be configured.") }
  }

}
