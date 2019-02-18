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

package fun.zhongl.passport
import scopt.OptionParser

object CommandLine {
  case class Opt(host: String = "0.0.0.0", port: Int = 8080, echo: Boolean = false, dynamic: Option[String] = None)

  val parser = new OptionParser[Opt]("passport") {
    head("passport", "0.0.1")

    opt[String]('h', "host")
      .action((x, c) => c.copy(host = x))
      .text("listened host address, default is 0.0.0.0")

    opt[Int]('p', "port")
      .action((x, c) => c.copy(port = x))
      .text("listened port, default is 8080")

    opt[Unit]('e', "echo")
      .action((_, c) => c.copy(echo = true))
      .text("enable echo mode for debug, default is disable")

    opt[String]('d', "dynamic")
      .action((x, c) => c.copy(dynamic = Some(x)))
      .validate {
        case "docker" | "swarm" => success
        case _                  => failure("Option -d or --dynamic must be docker or swarm.")
      }
      .text("enable dynamic dispatch, which's value must be docker or swarm, default is disable")

    help("help").text("print this usage")
  }

}
