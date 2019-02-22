package zhongl.passport

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, GraphDSL, Merge, Sink, Source}
import akka.stream.{ActorMaterializer, FlowShape}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class EitherForkSpec extends WordSpec with BeforeAndAfterAll with Matchers {
  implicit val system = ActorSystem(getClass.getSimpleName)
  implicit val mat    = ActorMaterializer()

  "EitherFork" should {
    "fork" in {

      val flow = Flow.fromGraph(GraphDSL.create() { implicit b =>
        import GraphDSL.Implicits._

        val fork = b.add(EitherFork[Int, String]())
        val toS  = b.add(Flow[Int].map(_.toString))
        val merge = b.add(Merge[String](2))

        // format: OFF
        fork.out0 ~> toS ~> merge
        fork.out1        ~> merge
        // format: ON

        new FlowShape(fork.in, merge.out)
      })

      val f = Source(List[Either[Int, String]](Left(1), Right("2"))).via(flow).runWith(Sink.seq)
      Await.result(f, Duration.Inf) shouldBe Vector("1", "2")
    }
  }

  override protected def afterAll(): Unit = system.terminate()
}
