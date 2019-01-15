package fun.zhongl.passport
import java.util.concurrent.TimeUnit

import com.auth0.jwt.algorithms.Algorithm
import com.typesafe.config.Config
import zhongl.stream.oauth2.JwtCookie

import scala.concurrent.duration.FiniteDuration

object JwtCookies {
  def load(conf: Config): JwtCookie = {
    val unit      = TimeUnit.DAYS
    val days      = conf.getDuration("cookie.expires_in", unit)
    val algorithm = Algorithm.HMAC256(conf.getString("cookie.secret"))
    JwtCookie(conf.getString("cookie.name"), conf.getString("cookie.domain"), algorithm, FiniteDuration(days, unit))
  }
}
