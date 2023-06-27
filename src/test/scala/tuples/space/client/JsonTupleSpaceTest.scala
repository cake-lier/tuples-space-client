/*
 * Copyright (c) 2023 Matteo Castellucci
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package io.github.cakelier
package tuples.space.client

import java.util.UUID
import java.util.concurrent.TimeoutException

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

import akka.actor.ActorSystem
import akka.actor.typed.ActorRef
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.Message
import akka.http.scaladsl.model.ws.TextMessage
import akka.http.scaladsl.server.Directives.*
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.stream.typed.scaladsl.ActorSource
import io.circe.parser.*
import io.circe.syntax.*
import org.scalatest.BeforeAndAfterAll
import org.scalatest.OptionValues.*
import org.scalatest.TryValues.*
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.*
import org.scalatest.time.Seconds
import org.scalatest.time.Span

import AnyOps.*
import tuples.space.*
import tuples.space.JsonTuple.JsonNil
import tuples.space.request.*
import tuples.space.request.Request.MergeRequest
import tuples.space.request.Serializers.given
import tuples.space.response.*
import tuples.space.response.Response.{ConnectionSuccessResponse, MergeSuccessResponse}
import tuples.space.response.Serializers.given

@SuppressWarnings(Array("org.wartremover.warts.Throw", "org.wartremover.warts.Var"))
class JsonTupleSpaceTest extends AnyFunSpec with BeforeAndAfterAll with Eventually {

  private var server: Option[Http.ServerBinding] = None
  private val tuple: JsonTuple = 1 #: "Example" #: JsonNil
  private val poisonPill: JsonTuple = "BOOM!" #: JsonNil
  private val template: JsonTemplate = tuples.space.complete(int gte 1, string matches "[A-Z]".r)
  private val foreverPendingTemplate: JsonTemplate = tuples.space.complete(nil)
  private val nonMatchingTemplate: JsonTemplate = tuples.space.partial(double)
  private var useFirstUUID: Boolean = true
  private val firstUUID: UUID = UUID.randomUUID()
  private val secondUUID: UUID = UUID.randomUUID()
  private var actor: Option[ActorRef[Message]] = None

  override def beforeAll(): Unit = {
    given ActorSystem = ActorSystem()
    server = Some(
      Await.result(
        Http()
          .newServerAt("localhost", 8080)
          .bind(path("") {
            handleWebSocketMessages {
              val (actorRef, actorSource) =
                ActorSource
                  .actorRef[Message](PartialFunction.empty, PartialFunction.empty, 100, OverflowStrategy.dropHead)
                  .preMaterialize()
              actor = Some(actorRef)
              actorRef ! TextMessage(ConnectionSuccessResponse(if (useFirstUUID) firstUUID else secondUUID).asJson.noSpaces)

              Flow[Message]
                .mapConcat {
                  case t: TextMessage.Strict =>
                    (for {
                      j <- parse(t.text)
                      m <- j.as[Request]
                      r = m match {
                        case t: TupleRequest if t.content === poisonPill => throw new RuntimeException()
                        case t: TupleRequest => TextMessage(TupleResponse(t.content).asJson.noSpaces) :: Nil
                        case t: TemplateRequest if t.content === foreverPendingTemplate => Nil
                        case t: TemplateRequest =>
                          t.tpe match {
                            case TemplateRequestType.In =>
                              TextMessage(
                                TemplateTupleResponse(t.content, TemplateTupleResponseType.In, tuple).asJson.noSpaces
                              ) :: Nil
                            case TemplateRequestType.Rd =>
                              TextMessage(
                                TemplateTupleResponse(t.content, TemplateTupleResponseType.Rd, tuple).asJson.noSpaces
                              ) :: Nil
                            case TemplateRequestType.No => TextMessage(TemplateResponse(t.content).asJson.noSpaces) :: Nil
                            case TemplateRequestType.Inp if t.content === template =>
                              TextMessage(
                                TemplateMaybeTupleResponse(t.content, TemplateMaybeTupleResponseType.Inp, Some(tuple))
                                  .asJson
                                  .noSpaces
                              ) :: Nil
                            case TemplateRequestType.Inp =>
                              TextMessage(
                                TemplateMaybeTupleResponse(t.content, TemplateMaybeTupleResponseType.Inp, None).asJson.noSpaces
                              ) :: Nil
                            case TemplateRequestType.Rdp if t.content === template =>
                              TextMessage(
                                TemplateMaybeTupleResponse(t.content, TemplateMaybeTupleResponseType.Rdp, Some(tuple))
                                  .asJson
                                  .noSpaces
                              ) :: Nil
                            case TemplateRequestType.Rdp =>
                              TextMessage(
                                TemplateMaybeTupleResponse(t.content, TemplateMaybeTupleResponseType.Rdp, None).asJson.noSpaces
                              ) :: Nil
                            case TemplateRequestType.Nop if t.content === template =>
                              TextMessage(TemplateBooleanResponse(t.content, true).asJson.noSpaces) :: Nil
                            case TemplateRequestType.Nop =>
                              TextMessage(TemplateBooleanResponse(t.content, false).asJson.noSpaces) :: Nil
                            case TemplateRequestType.InAll if t.content === template =>
                              TextMessage(
                                TemplateSeqTupleResponse(t.content, TemplateSeqTupleResponseType.InAll, Seq(tuple))
                                  .asJson
                                  .noSpaces
                              ) :: Nil
                            case TemplateRequestType.InAll =>
                              TextMessage(
                                TemplateSeqTupleResponse(t.content, TemplateSeqTupleResponseType.InAll, Seq.empty).asJson.noSpaces
                              ) :: Nil
                            case TemplateRequestType.RdAll if t.content === template =>
                              TextMessage(
                                TemplateSeqTupleResponse(t.content, TemplateSeqTupleResponseType.RdAll, Seq(tuple))
                                  .asJson
                                  .noSpaces
                              ) :: Nil
                            case TemplateRequestType.RdAll =>
                              TextMessage(
                                TemplateSeqTupleResponse(t.content, TemplateSeqTupleResponseType.RdAll, Seq.empty).asJson.noSpaces
                              ) :: Nil
                          }
                        case t: SeqTupleRequest => TextMessage(SeqTupleResponse(t.content).asJson.noSpaces) :: Nil
                        case t: MergeRequest =>
                          t.clientId shouldBe secondUUID
                          t.oldClientId shouldBe firstUUID
                          TextMessage(MergeSuccessResponse(t.clientId, t.oldClientId).asJson.noSpaces) :: Nil
                      }
                    } yield r).toOption.getOrElse(Nil)
                  case m => Nil
                }
                .via(Flow.fromSinkAndSourceCoupled(Sink.foreach(m => actor.foreach(_ ! m)), actorSource))
            }
          }),
        Integer.MAX_VALUE.seconds
      )
    )
  }

  override def afterAll(): Unit = Await.result(server.getOrElse(fail()).unbind(), Integer.MAX_VALUE.seconds)

  describe("A tuple space") {
    describe("when the server is active") {
      it("should correctly connect to it") {
        val client: Future[JsonTupleSpace] = JsonTupleSpace("ws://localhost:8080/")
        val tupleSpace: JsonTupleSpace = eventually(Timeout(Span(10, Seconds))) {
          client.value.value.success.value
        }
        Await.result(tupleSpace.close(), 10.seconds)
      }
    }

    describe("when the server is inactive") {
      it("should not connect to it") {
        val client: Future[JsonTupleSpace] = JsonTupleSpace("ws://localhost:8081/")
        a[TimeoutException] should be thrownBy Await.ready(client, 10.seconds)
      }
    }

    describe("when the server is active but the path is wrong") {
      it("should not connect to it") {
        val client: Future[JsonTupleSpace] = JsonTupleSpace("ws://localhost:8080/hello")
        a[TimeoutException] should be thrownBy Await.ready(client, 10.seconds)
      }
    }

    describe("when an out operation is issued") {
      it("should complete with success") {
        val client: JsonTupleSpace = Await.result(JsonTupleSpace("ws://localhost:8080/"), Integer.MAX_VALUE.seconds)
        val result: Future[Unit] = client.out(tuple)
        eventually(Timeout(Span(10, Seconds))) {
          result.value.value.success.value
        } shouldBe ()
        Await.result(client.close(), 10.seconds)
      }
    }

    describe("when an in operation is issued") {
      it("should complete with success") {
        val client: JsonTupleSpace = Await.result(JsonTupleSpace("ws://localhost:8080/"), Integer.MAX_VALUE.seconds)
        val result: Future[JsonTuple] = client.in(template)
        eventually(Timeout(Span(10, Seconds))) {
          result.value.value.success.value
        } shouldBe tuple
        Await.result(client.close(), 10.seconds)
      }
    }

    describe("when a rd operation is issued") {
      it("should complete with success") {
        val client: JsonTupleSpace = Await.result(JsonTupleSpace("ws://localhost:8080/"), Integer.MAX_VALUE.seconds)
        val result: Future[JsonTuple] = client.rd(template)
        eventually(Timeout(Span(10, Seconds))) {
          result.value.value.success.value
        } shouldBe tuple
        Await.result(client.close(), 10.seconds)
      }
    }

    describe("when a no operation is issued") {
      it("should complete with success") {
        val client: JsonTupleSpace = Await.result(JsonTupleSpace("ws://localhost:8080/"), Integer.MAX_VALUE.seconds)
        val result: Future[Unit] = client.no(template)
        eventually(Timeout(Span(10, Seconds))) {
          result.value.value.success.value
        } shouldBe ()
        Await.result(client.close(), 10.seconds)
      }
    }

    describe("when an operation is issued after closing the client") {
      it("should complete with failure") {
        val client: JsonTupleSpace = Await.result(JsonTupleSpace("ws://localhost:8080/"), Integer.MAX_VALUE.seconds)
        Await.result(client.close(), 10.seconds)
        val result: Future[Unit] = client.no(template)
        val ex: Throwable = eventually(Timeout(Span(10, Seconds))) {
          result.value.value.failure.exception
        }
        ex shouldBe a[IllegalStateException]
        ex should have message "The queue was completed."
      }
    }

    describe("when is closed twice") {
      it("should do nothing") {
        val client: JsonTupleSpace = Await.result(JsonTupleSpace("ws://localhost:8080/"), Integer.MAX_VALUE.seconds)
        val closeResult1 = client.close()
        eventually(Timeout(Span(10, Seconds))) {
          closeResult1.value.value.success.value
        }
        val closeResult2 = client.close()
        eventually(Timeout(Span(10, Seconds))) {
          closeResult2.value.value.success.value
        }
      }
    }

    describe("when an operation is issued and before completing it the client is closed") {
      it("should complete with failure") {
        val client: JsonTupleSpace = Await.result(JsonTupleSpace("ws://localhost:8080/"), Integer.MAX_VALUE.seconds)
        val result: Future[JsonTuple] = client.in(foreverPendingTemplate)
        Await.result(client.close(), 10.seconds)
        val ex: Throwable = eventually(Timeout(Span(10, Seconds))) {
          result.value.value.failure.exception
        }
        ex shouldBe a[IllegalStateException]
        ex should have message "The queue was completed."
      }
    }

    describe("when an inp operation with a template matching an existing tuple is issued") {
      it("should complete with success") {
        val client: JsonTupleSpace = Await.result(JsonTupleSpace("ws://localhost:8080/"), Integer.MAX_VALUE.seconds)
        val result: Future[Option[JsonTuple]] = client.inp(template)
        eventually(Timeout(Span(10, Seconds))) {
          result.value.value.success.value
        } shouldBe Some(tuple)
        Await.result(client.close(), 10.seconds)
      }
    }

    describe("when an inp operation with a template matching an non-existing tuple is issued") {
      it("should complete with success") {
        val client: JsonTupleSpace = Await.result(JsonTupleSpace("ws://localhost:8080/"), Integer.MAX_VALUE.seconds)
        val result: Future[Option[JsonTuple]] = client.inp(nonMatchingTemplate)
        eventually(Timeout(Span(10, Seconds))) {
          result.value.value.success.value
        } shouldBe None
        Await.result(client.close(), 10.seconds)
      }
    }

    describe("when a rdp operation with a template matching an existing tuple is issued") {
      it("should complete with success") {
        val client: JsonTupleSpace = Await.result(JsonTupleSpace("ws://localhost:8080/"), Integer.MAX_VALUE.seconds)
        val result: Future[Option[JsonTuple]] = client.rdp(template)
        eventually(Timeout(Span(10, Seconds))) {
          result.value.value.success.value
        } shouldBe Some(tuple)
        Await.result(client.close(), 10.seconds)
      }
    }

    describe("when a rdp operation with a template matching an non-existing tuple is issued") {
      it("should complete with success") {
        val client: JsonTupleSpace = Await.result(JsonTupleSpace("ws://localhost:8080/"), Integer.MAX_VALUE.seconds)
        val result: Future[Option[JsonTuple]] = client.rdp(nonMatchingTemplate)
        eventually(Timeout(Span(10, Seconds))) {
          result.value.value.success.value
        } shouldBe None
        Await.result(client.close(), 10.seconds)
      }
    }

    describe("when a nop operation with a template matching an existing tuple is issued") {
      it("should complete with success") {
        val client: JsonTupleSpace = Await.result(JsonTupleSpace("ws://localhost:8080/"), Integer.MAX_VALUE.seconds)
        val result: Future[Boolean] = client.nop(template)
        eventually(Timeout(Span(10, Seconds))) {
          result.value.value.success.value
        } shouldBe true
        Await.result(client.close(), 10.seconds)
      }
    }

    describe("when a nop operation with a template matching an non-existing tuple is issued") {
      it("should complete with success") {
        val client: JsonTupleSpace = Await.result(JsonTupleSpace("ws://localhost:8080/"), Integer.MAX_VALUE.seconds)
        val result: Future[Boolean] = client.nop(nonMatchingTemplate)
        eventually(Timeout(Span(10, Seconds))) {
          result.value.value.success.value
        } shouldBe false
        Await.result(client.close(), 10.seconds)
      }
    }

    describe("when an inAll operation with a template matching an existing tuple is issued") {
      it("should complete with success") {
        val client: JsonTupleSpace = Await.result(JsonTupleSpace("ws://localhost:8080/"), Integer.MAX_VALUE.seconds)
        val result: Future[Seq[JsonTuple]] = client.inAll(template)
        eventually(Timeout(Span(10, Seconds))) {
          result.value.value.success.value
        } shouldBe Seq(tuple)
        Await.result(client.close(), 10.seconds)
      }
    }

    describe("when an inAll operation with a template matching an non-existing tuple is issued") {
      it("should complete with success") {
        val client: JsonTupleSpace = Await.result(JsonTupleSpace("ws://localhost:8080/"), Integer.MAX_VALUE.seconds)
        val result: Future[Seq[JsonTuple]] = client.inAll(nonMatchingTemplate)
        eventually(Timeout(Span(10, Seconds))) {
          result.value.value.success.value
        } shouldBe Seq.empty[JsonTuple]
        Await.result(client.close(), 10.seconds)
      }
    }

    describe("when a rdAll operation with a template matching an existing tuple is issued") {
      it("should complete with success") {
        val client: JsonTupleSpace = Await.result(JsonTupleSpace("ws://localhost:8080/"), Integer.MAX_VALUE.seconds)
        val result: Future[Seq[JsonTuple]] = client.rdAll(template)
        eventually(Timeout(Span(10, Seconds))) {
          result.value.value.success.value
        } shouldBe Seq(tuple)
        Await.result(client.close(), 10.seconds)
      }
    }

    describe("when a rdAll operation with a template matching an non-existing tuple is issued") {
      it("should complete with success") {
        val client: JsonTupleSpace = Await.result(JsonTupleSpace("ws://localhost:8080/"), Integer.MAX_VALUE.seconds)
        val result: Future[Seq[JsonTuple]] = client.rdAll(nonMatchingTemplate)
        eventually(Timeout(Span(10, Seconds))) {
          result.value.value.success.value
        } shouldBe Seq.empty[JsonTuple]
        Await.result(client.close(), 10.seconds)
      }
    }

    describe("when a disconnection occurs") {
      it("should reconnect with success, asking for its old client id") {
        val client: JsonTupleSpace = Await.result(JsonTupleSpace("ws://localhost:8080/"), Integer.MAX_VALUE.seconds)
        val outResult = client.out(tuple)
        eventually(Timeout(Span(10, Seconds))) {
          outResult.value.value.success.value
        } shouldBe ()
        useFirstUUID = false
        a[IllegalStateException] should be thrownBy Await.result(client.out(poisonPill), Integer.MAX_VALUE.seconds)
        val inResult = client.in(template)
        eventually(Timeout(Span(10, Seconds))) {
          inResult.value.value.success.value
        } shouldBe tuple
        Await.result(client.close(), 10.seconds)
      }
    }
  }
}
