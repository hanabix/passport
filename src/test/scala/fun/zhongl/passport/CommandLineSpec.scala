package fun.zhongl.passport
import org.scalatest.{Matchers, WordSpec}

class CommandLineSpec extends WordSpec with Matchers {
  val default = CommandLine.Opt()

  "CommandLine" should {
    "parse opt" in {
      CommandLine.parser.parse(Seq(), default) shouldBe Some(default)
    }
  }
}
