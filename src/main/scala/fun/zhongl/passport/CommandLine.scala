package fun.zhongl.passport
import scopt.OptionParser

object CommandLine {
  case class Opt(host: String = "0.0.0.0", port: Int = 8080, echo: Boolean = false)

  val parser = new OptionParser[Opt]("passport") {
    head("passport", "0.0.1")

    opt[String]('h', "host").action((x, c) => c.copy(host = x)).text("listened host address, default is 0.0.0.0")
    opt[Int]('p', "port").action((x, c) => c.copy(port = x)).text("listened port, default is 8080")
    opt[Unit]('e', "echo").action((_, c) => c.copy(echo = true)).text("enable echo mode for debug, default is disable")

    help("help").text("print this usage")
  }

}
