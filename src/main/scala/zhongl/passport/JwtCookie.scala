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

import java.util.concurrent.TimeUnit

import com.auth0.jwt.algorithms.Algorithm
import com.typesafe.config.Config
import zhongl.stream.oauth2.{JwtCookie => JC}

import scala.concurrent.duration.FiniteDuration

object JwtCookie {
  def apply(conf: Config): JC = {
    val unit      = TimeUnit.DAYS
    val days      = conf.getDuration("cookie.expires_in", unit)
    val algorithm = Algorithm.HMAC256(conf.getString("cookie.secret"))
    JC(conf.getString("cookie.name"), conf.getString("cookie.domain"), algorithm, FiniteDuration(days, unit))
  }
}
