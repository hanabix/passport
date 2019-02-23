package zhongl.passport

import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.Host
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.Timeout
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.control.{NoStackTrace, NonFatal}

class RewriteRequestActorSpec
    extends TestKit(ActorSystem("RewriteRequest"))
    with WordSpecLike
    with Matchers
    with ImplicitSender
    with BeforeAndAfterAll {

  private implicit val mat     = ActorMaterializer()
  private implicit val timeout = Timeout(3.seconds)

  "RewriteRequestActor" should {

    "handle request after locate function updated" in {
      val ref = system.actorOf(RewriteRequestActor.props(Source.repeat(List(".+".r -> Host("demo"))).delay(1.seconds)))
      val f = Source
        .single(HttpRequest(uri = "http://localhost", headers = List(Host("localhost"))))
        .ask[Either[HttpResponse, HttpRequest]](ref)
        .runWith(Sink.head)
      Await.result(f, Duration.Inf) shouldBe Right(HttpRequest(uri = "http://demo", headers = List(Host("localhost"))))
    }

    "stop self after rule source failed" in {
      def test(source: Source[Docker.Mode.Rules, Any])(cause: Throwable) = {
        val result = Left(HttpResponse(StatusCodes.InternalServerError))
        val ref    = system.actorOf(RewriteRequestActor.props(source))
        val f = Source
          .single(HttpRequest()).delay(1.second)
          .ask[Either[HttpResponse, HttpRequest]](ref)
          .recover { case NonFatal(`cause`) => result }
          .runWith(Sink.head)
        Await.result(f, Duration.Inf) shouldBe result
      }

      val cause = new Exception with NoStackTrace

      test(Source.failed(cause))(cause)
      test(Source(0 to 1).map(i => if (i == 0) List(".+".r -> Host("demo")) else throw cause))(cause)
    }

    "stop self after rules source complete" in {
      def test(source: Source[Docker.Mode.Rules, Any]) = {
        val probe = TestProbe()
        val ref   = system.actorOf(RewriteRequestActor.props(source))
        probe.watch(ref)
        probe.expectTerminated(ref)
      }

      test(Source.empty)
      test(Source.single(List(".+".r -> Host("demo"))))
    }
  }

  override protected def afterAll(): Unit = TestKit.shutdownActorSystem(system)
}
