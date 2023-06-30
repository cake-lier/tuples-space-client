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

import scala.collection.mutable
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration.DurationInt
import scala.util.Failure
import scala.util.Success

import akka.Done
import akka.NotUsed
import akka.actor.ActorSystem
import akka.actor.typed.ActorRef
import akka.http.scaladsl.Http
import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.*
import akka.stream.OverflowStrategy
import akka.stream.QueueCompletionResult
import akka.stream.QueueOfferResult
import akka.stream.RestartSettings
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.RestartFlow
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.SourceQueueWithComplete
import io.circe.parser.*
import io.circe.syntax.*

import tuples.space.*
import tuples.space.request.*
import tuples.space.request.RequestSerializer.given
import tuples.space.response.*
import tuples.space.response.ResponseDeserializer.given
import AnyOps.*

trait JsonTupleSpace {

  def out(t: JsonTuple): Future[Unit]

  def rd(tt: JsonTemplate): Future[JsonTuple]

  def in(tt: JsonTemplate): Future[JsonTuple]

  def no(tt: JsonTemplate): Future[Unit]

  def outAll(ts: JsonTuple*): Future[Unit]

  def rdAll(tt: JsonTemplate): Future[Seq[JsonTuple]]

  def inAll(tt: JsonTemplate): Future[Seq[JsonTuple]]

  def rdp(tt: JsonTemplate): Future[Option[JsonTuple]]

  def inp(tt: JsonTemplate): Future[Option[JsonTuple]]

  def nop(tt: JsonTemplate): Future[Boolean]

  def close(): Future[Unit]
}

object JsonTupleSpace {

  @SuppressWarnings(
    Array(
      "org.wartremover.warts.MutableDataStructures",
      "org.wartremover.warts.Null",
      "org.wartremover.warts.Var",
      "scalafix:DisableSyntax.var",
      "scalafix:DisableSyntax.null"
    )
  )
  private class JsonTupleSpaceImpl(
    uri: String,
    bufferSize: Int,
    completionPromise: Promise[Unit]
  )(
    using
    system: ActorSystem
  ) extends JsonTupleSpace {

    import system.dispatcher

    private val tupleRequests: mutable.Buffer[(JsonTuple, Promise[Unit])] = mutable.Buffer.empty
    private val templateRdRequests: mutable.Buffer[(JsonTemplate, Promise[JsonTuple])] = mutable.Buffer.empty
    private val templateInRequests: mutable.Buffer[(JsonTemplate, Promise[JsonTuple])] = mutable.Buffer.empty
    private val templateNoRequests: mutable.Buffer[(JsonTemplate, Promise[Unit])] = mutable.Buffer.empty
    private val templateRdpRequests: mutable.Buffer[(JsonTemplate, Promise[Option[JsonTuple]])] = mutable.Buffer.empty
    private val templateInpRequests: mutable.Buffer[(JsonTemplate, Promise[Option[JsonTuple]])] = mutable.Buffer.empty
    private val templateNopRequests: mutable.Buffer[(JsonTemplate, Promise[Boolean])] = mutable.Buffer.empty
    private val templateRdAllRequests: mutable.Buffer[(JsonTemplate, Promise[Seq[JsonTuple]])] = mutable.Buffer.empty
    private val templateInAllRequests: mutable.Buffer[(JsonTemplate, Promise[Seq[JsonTuple]])] = mutable.Buffer.empty
    private val tupleSeqRequests: mutable.Buffer[(Seq[JsonTuple], Promise[Unit])] = mutable.Buffer.empty

    private var clientId: Option[UUID] = None
    private var connectionCompletion: Promise[Unit] = Promise()
    private val httpClient: HttpExt = Http()

    private val restartSettings: RestartSettings = RestartSettings(
      minBackoff = 1.second,
      maxBackoff = 30.seconds,
      randomFactor = 0.1
    )

    private val (queue: SourceQueueWithComplete[Message], source: Source[Message, NotUsed]) =
      Source.queue[Message](bufferSize, OverflowStrategy.dropHead).preMaterialize()

    private val flowCompletion: Future[Done] =
      source
        .via(
          RestartFlow.withBackoff(restartSettings)(() =>
            httpClient
              .webSocketClientFlow(WebSocketRequest(uri))
              .mapError(t => {
                synchronized {
                  failAllRemaining()
                  connectionCompletion = Promise()
                }
                t
              })
          )
        )
        .mapAsync(1) {
          case m: TextMessage.Strict => Future.successful(m)
          case m: TextMessage.Streamed => m.textStream.runFold("")(_ + _).map(TextMessage.apply)
          case _ => Future.successful(null)
        }
        .flatMapConcat(t =>
          (for {
            j <- parse(t.text)
            m <- j.as[Response]
          } yield m).map(Source.single[Response]).getOrElse(Source.empty[Response])
        )
        .runWith(Sink.foreach {
          case r: ConnectionSuccessResponse =>
            synchronized {
              clientId match {
                case Some(id) =>
                  queue.offer(TextMessage((MergeRequest(r.clientId, id): Request).asJson.noSpaces)).foreach {
                    case QueueOfferResult.Enqueued => ()
                    case QueueOfferResult.Dropped =>
                      connectionCompletion.failure(IllegalStateException("The request was dropped from its queue."))
                    case QueueOfferResult.Failure(e) => connectionCompletion.failure(e)
                    case QueueOfferResult.QueueClosed =>
                      connectionCompletion.failure(IllegalStateException("The queue was completed."))
                  }
                case None =>
                  clientId = Some(r.clientId)
                  connectionCompletion.success(())
                  completionPromise.success(())
              }
            }
          case r: MergeSuccessResponse => synchronized { connectionCompletion.success(()) }
          case r: TupleResponse => completeRequest(r.request, tupleRequests, ())
          case r: SeqTupleResponse => completeRequest(r.request, tupleSeqRequests, ())
          case r: TemplateTupleResponse =>
            r.tpe match {
              case TemplateTupleResponseType.In => completeRequest(r.request, templateInRequests, r.content)
              case TemplateTupleResponseType.Rd => completeRequest(r.request, templateRdRequests, r.content)
            }
          case r: TemplateMaybeTupleResponse =>
            r.tpe match {
              case TemplateMaybeTupleResponseType.Inp => completeRequest(r.request, templateInpRequests, r.content)
              case TemplateMaybeTupleResponseType.Rdp => completeRequest(r.request, templateRdpRequests, r.content)
            }
          case r: TemplateSeqTupleResponse =>
            r.tpe match {
              case TemplateSeqTupleResponseType.InAll => completeRequest(r.request, templateInAllRequests, r.content)
              case TemplateSeqTupleResponseType.RdAll => completeRequest(r.request, templateRdAllRequests, r.content)
            }
          case r: TemplateResponse => completeRequest(r.request, templateNoRequests, ())
          case r: TemplateBooleanResponse => completeRequest(r.request, templateNopRequests, r.content)
        })
    flowCompletion.onComplete(_ => synchronized { failAllRemaining() })

    private def failAllRemaining(): Unit =
      Seq(tupleRequests, templateRdRequests, templateInRequests, templateNoRequests)
        .flatten
        .map(_._2)
        .foreach(_.failure(IllegalStateException("The queue was completed.")))

    private def completeRequest[A, B](
      request: A,
      pendingRequests: mutable.Buffer[(A, Promise[B])],
      response: B
    ): Unit = synchronized {
      pendingRequests
        .find((r, _) => r === request)
        .foreach((r, p) => {
          p.success(response)
          pendingRequests -= r -> p
        })
    }

    private def failWithThrowable[A, B](
      e: Throwable
    )(
      content: A,
      requestPromise: Promise[B],
      pendingRequests: mutable.Buffer[(A, Promise[B])]
    ): Future[B] = {
      synchronized { pendingRequests -= content -> requestPromise }
      Future.failed[B](e)
    }

    private def doRequest[A, B](
      content: A,
      requestBuilder: A => Request,
      pendingRequests: mutable.Buffer[(A, Promise[B])]
    ): Future[B] = connectionCompletion.future.flatMap { _ =>
      val requestPromise = Promise[B]()
      synchronized { pendingRequests += content -> requestPromise }
      queue
        .offer(TextMessage.Strict(requestBuilder(content).asJson.noSpaces))
        .flatMap {
          case QueueOfferResult.Enqueued => requestPromise.future
          case QueueOfferResult.Dropped =>
            failWithThrowable(
              IllegalStateException("The request was dropped from its queue.")
            )(
              content,
              requestPromise,
              pendingRequests
            )
          case QueueOfferResult.QueueClosed =>
            failWithThrowable(
              IllegalStateException("The queue was completed.")
            )(
              content,
              requestPromise,
              pendingRequests
            )
          case QueueOfferResult.Failure(e) => failWithThrowable(e)(content, requestPromise, pendingRequests)
        }
        .transform {
          case Failure(_) => Failure[B](IllegalStateException("The queue was completed."))
          case s => s
        }
    }

    override def out(t: JsonTuple): Future[Unit] = doRequest(t, TupleRequest.apply, tupleRequests)

    override def rd(tt: JsonTemplate): Future[JsonTuple] =
      doRequest(tt, TemplateRequest(_, TemplateRequestType.Rd), templateRdRequests)

    override def in(tt: JsonTemplate): Future[JsonTuple] =
      doRequest(tt, TemplateRequest(_, TemplateRequestType.In), templateInRequests)

    override def no(tt: JsonTemplate): Future[Unit] =
      doRequest(tt, TemplateRequest(_, TemplateRequestType.No), templateNoRequests)

    override def outAll(ts: JsonTuple*): Future[Unit] =
      doRequest(ts, SeqTupleRequest.apply, tupleSeqRequests)

    override def rdAll(tt: JsonTemplate): Future[Seq[JsonTuple]] =
      doRequest(tt, TemplateRequest(_, TemplateRequestType.RdAll), templateRdAllRequests)

    override def inAll(tt: JsonTemplate): Future[Seq[JsonTuple]] =
      doRequest(tt, TemplateRequest(_, TemplateRequestType.InAll), templateInAllRequests)

    override def nop(tt: JsonTemplate): Future[Boolean] =
      doRequest(tt, TemplateRequest(_, TemplateRequestType.Nop), templateNopRequests)

    override def inp(tt: JsonTemplate): Future[Option[JsonTuple]] =
      doRequest(tt, TemplateRequest(_, TemplateRequestType.Inp), templateInpRequests)

    override def rdp(tt: JsonTemplate): Future[Option[JsonTuple]] =
      doRequest(tt, TemplateRequest(_, TemplateRequestType.Rdp), templateRdpRequests)

    override def close(): Future[Unit] = {
      queue.complete()
      flowCompletion.map(_ => ())
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments", "scalafix:DisableSyntax.defaultArgs"))
  def apply(uri: String, bufferSize: Int = 1)(using system: ActorSystem = ActorSystem()): Future[JsonTupleSpace] = {
    import system.dispatcher

    val completionPromise: Promise[Unit] = Promise[Unit]()
    val tupleSpace = JsonTupleSpaceImpl(uri, bufferSize, completionPromise)
    completionPromise.future.map(_ => tupleSpace)
  }
}
