package zhongl.passport

import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.{Flow, GraphDSL, Merge, Sink, Source}
import akka.testkit.TestKit
import org.scalatest._

class EitherForkSpec extends TestKit(ActorSystem("either")) with AsyncWordSpecLike with BeforeAndAfterAll with Matchers {
  implicit val mat = ActorMaterializer()

  "EitherFork" should {
    "fork" in {

      val flow = Flow.fromGraph(GraphDSL.create() { implicit b =>
        import GraphDSL.Implicits._

        val fork  = b.add(EitherFork[Int, String]())
        val toS   = b.add(Flow[Int].map(_.toString))
        val merge = b.add(Merge[String](2))

        // format: OFF
        fork.out0 ~> toS ~> merge
        fork.out1        ~> merge
        // format: ON

        new FlowShape(fork.in, merge.out)
      })

      Source(List[Either[Int, String]](Left(1), Right("2"))).via(flow).runWith(Sink.seq).map(_ shouldBe Vector("1", "2"))
    }
  }

  override protected def afterAll(): Unit = TestKit.shutdownActorSystem(system)
}
