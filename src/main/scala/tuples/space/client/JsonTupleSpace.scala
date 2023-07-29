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
import tuples.space.client.request.*
import request.RequestSerializer.given
import tuples.space.client.response.*
import response.ResponseDeserializer.given
import AnyOps.*

/** A coordination medium to be used to exchange pieces of information and coordinate with other entities, implemented to be used
  * with [[JsonTuple]]s and [[JsonTemplate]]s.
  *
  * A "tuple space", in general, is what is called a coordination medium. It is a way to coordinate the entities that participate
  * in its use. It is a way to exchange information, more specifically tuples and with this implementation [[JsonTuple]]s, and to
  * coordinate the different actions of the entities. For a "tuple space", the coordination happens with the same operations that
  * let to write and to read into the space. The basic operations, "in" and "rd", have a suspensive semantic, which means that
  * their completion suspends until a tuple able to complete them is found in the space. In this way, similarly to the "future"
  * data structure, the execution can be paused until the result is ready. Matching a tuple means to have a template to be used by
  * the operation for matching, which in this implementation is a [[JsonTemplate]].
  */
trait JsonTupleSpace {

  /** The operation for inserting a [[JsonTuple]] into this [[JsonTupleSpace]]. This is one of the core operations on the tuple
    * space, along with "in" and "rd". Differently from these two, this operation is not suspensive: it completes right away,
    * because it is always allowed to insert a new tuple into the space. A [[Future]] is still returned because the actual tuple
    * space can be hosted on a remote host, meaning that the operation is in fact a network operation that takes time to complete.
    * The future will complete signalling only the success of the operation.
    *
    * @param t
    *   the [[JsonTuple]] to be inserted into this [[JsonTupleSpace]]
    * @return
    *   a [[Future]] which completes when the operation has completed
    */
  def out(t: JsonTuple): Future[Unit]

  /** The operation for reading a [[JsonTuple]] into this [[JsonTupleSpace]]. This is one of the core operations on the tuple
    * space, along with "in" and "out". This is a suspensive operation, it will complete only when in the space a tuple matching
    * the template of this operation is found. This also mean that the operation will not suspend at all, if a tuple is already
    * inside the space. If multiple tuples matching the template are inside the space, one will be chosen randomly, following the
    * "don't care" nondeterminism. The tuple matched is not removed from the tuple space. A [[Future]] is still returned because
    * the actual tuple space can be hosted on a remote host, meaning that the operation is in fact a network operation that takes
    * time to complete. The future will complete with the matched tuple.
    *
    * @param tt
    *   the [[JsonTemplate]] to be used for matching a [[JsonTuple]] to be read in this [[JsonTupleSpace]]
    * @return
    *   a [[Future]] which completes when the operation has completed with the read [[JsonTuple]]
    */
  def rd(tt: JsonTemplate): Future[JsonTuple]

  /** The operation for taking a [[JsonTuple]] from this [[JsonTupleSpace]]. This is one of the core operations on the tuple
    * space, along with "rd" and "out". This is a suspensive operation, it will complete only when in the space a tuple matching
    * the template of this operation is found. This also mean that the operation will not suspend at all, if a tuple is already
    * inside the space. If multiple tuples matching the template are inside the space, one will be chosen randomly, following the
    * "don't care" nondeterminism. The tuple matched is then removed from the tuple space. A [[Future]] is still returned because
    * the actual tuple space can be hosted on a remote host, meaning that the operation is in fact a network operation that takes
    * time to complete. The future will complete with the matched tuple.
    *
    * @param tt
    *   the [[JsonTemplate]] to be used for matching a [[JsonTuple]] to be taken in this [[JsonTupleSpace]]
    * @return
    *   a [[Future]] which completes when the operation has completed with the taken [[JsonTuple]]
    */
  def in(tt: JsonTemplate): Future[JsonTuple]

  /** The operation for checking if some [[JsonTuple]]s are not into this [[JsonTupleSpace]]. This is a suspensive operation, it
    * will complete only when in the space no tuple matching the template of this operation is found. This also mean that the
    * operation will not suspend at all, if no tuple is already inside the space. If multiple tuples matching the template are
    * inside the space, only when the last one is removed the operation will complete. A [[Future]] is still returned because the
    * actual tuple space can be hosted on a remote host, meaning that the operation is in fact a network operation that takes time
    * to complete. The future will complete signalling only the success of the operation.
    *
    * @param tt
    *   the [[JsonTemplate]] to be used for matching [[JsonTuple]]s which should not be in this [[JsonTupleSpace]]
    * @return
    *   a [[Future]] which completes when the operation has completed
    */
  def no(tt: JsonTemplate): Future[Unit]

  /** The operation for inserting multiple [[JsonTuple]]s into this [[JsonTupleSpace]]. This is the "bulk" version of the basic
    * "out" operation. As for its basic counterpart, this operation is not suspensive: it completes right away, because it is
    * always allowed to insert new tuples into the space. A [[Future]] is still returned because the actual tuple space can be
    * hosted on a remote host, meaning that the operation is in fact a network operation that takes time to complete. The future
    * will complete signalling only the success of the operation.
    *
    * @param ts
    *   the [[JsonTuple]]s to be inserted into this [[JsonTupleSpace]]
    * @return
    *   a [[Future]] which completes when the operation has completed
    */
  def outAll(ts: JsonTuple*): Future[Unit]

  /** The operation for reading some [[JsonTuple]]s into this [[JsonTupleSpace]]. This is the "bulk" version of the basic "rd"
    * operation. Differently from its basic counterpart, this is not a suspensive operation, if no tuples matching the given
    * template are found, an empty [[Seq]] is returned. If multiple tuples matching the template are inside the space, all will be
    * returned in a [[Seq]]. The tuples matched are not removed from the tuple space. A [[Future]] is still returned because the
    * actual tuple space can be hosted on a remote host, meaning that the operation is in fact a network operation that takes time
    * to complete. The future will complete with the matched tuples.
    *
    * @param tt
    *   the [[JsonTemplate]] to be used for matching some [[JsonTuple]]s to be read in this [[JsonTupleSpace]]
    * @return
    *   a [[Future]] which completes when the operation has completed with the read [[JsonTuple]]s
    */
  def rdAll(tt: JsonTemplate): Future[Seq[JsonTuple]]

  /** The operation for taking some [[JsonTuple]]s from this [[JsonTupleSpace]]. This is the "bulk" version of the basic "in"
    * operation. Differently from its basic counterpart, this is not a suspensive operation, if no tuples matching the given
    * template are found, an empty [[Seq]] is returned. If multiple tuples matching the template are inside the space, all will be
    * returned in a [[Seq]]. The tuples matched are removed from the tuple space. A [[Future]] is still returned because the
    * actual tuple space can be hosted on a remote host, meaning that the operation is in fact a network operation that takes time
    * to complete. The future will complete with the matched tuples.
    *
    * @param tt
    *   the [[JsonTemplate]] to be used for matching some [[JsonTuple]]s to be taken from this [[JsonTupleSpace]]
    * @return
    *   a [[Future]] which completes when the operation has completed with the taken [[JsonTuple]]s
    */
  def inAll(tt: JsonTemplate): Future[Seq[JsonTuple]]

  /** The operation for reading a [[JsonTuple]] into this [[JsonTupleSpace]]. This is the "predicative" version of the basic "rd"
    * operation: this means that it's not a suspensive operation. If no tuples matching the given template are found, a [[None]]
    * is returned. If multiple tuples matching the template are inside the space, one will be chosen randomly, following the
    * "don't care" nondeterminism. The tuple matched is not removed from the tuple space. A [[Future]] is still returned because
    * the actual tuple space can be hosted on a remote host, meaning that the operation is in fact a network operation that takes
    * time to complete. The future will complete with a [[Some]] containing the matched tuple, if any.
    *
    * @param tt
    *   the [[JsonTemplate]] to be used for matching a [[JsonTuple]] to be read in this [[JsonTupleSpace]]
    * @return
    *   a [[Future]] which completes when the operation has completed with a [[Some]] containing the read [[JsonTuple]], if
    *   present, a [[None]] otherwise
    */
  def rdp(tt: JsonTemplate): Future[Option[JsonTuple]]

  /** The operation for taking a [[JsonTuple]] from this [[JsonTupleSpace]]. This is the "predicative" version of the basic "in"
    * operation: this means that it's not a suspensive operation. If no tuples matching the given template are found, a [[None]]
    * is returned. If multiple tuples matching the template are inside the space, one will be chosen randomly, following the
    * "don't care" nondeterminism. The tuple matched is then removed from the tuple space. A [[Future]] is still returned because
    * the actual tuple space can be hosted on a remote host, meaning that the operation is in fact a network operation that takes
    * time to complete. The future will complete with a [[Some]] containing the matched tuple, if any.
    *
    * @param tt
    *   the [[JsonTemplate]] to be used for matching a [[JsonTuple]] to be taken from this [[JsonTupleSpace]]
    * @return
    *   a [[Future]] which completes when the operation has completed with a [[Some]] containing the taken [[JsonTuple]], if
    *   present, a [[None]] otherwise
    */
  def inp(tt: JsonTemplate): Future[Option[JsonTuple]]

  /** The operation for checking if some [[JsonTuple]]s are not into this [[JsonTupleSpace]]. This is the "predicative" version of
    * the basic "no" operation: this means that it's not a suspensive operation. If no tuples matching the given template are
    * found, <code>true</code> is returned, otherwise <code>false</code> is returned. The tuples matched are not removed from the
    * tuple space. A [[Future]] is still returned because the actual tuple space can be hosted on a remote host, meaning that the
    * operation is in fact a network operation that takes time to complete. The future will complete with a boolean with the
    * result of the operation.
    *
    * @param tt
    *   the [[JsonTemplate]] to be used for matching [[JsonTuple]]s which should not be in this [[JsonTupleSpace]]
    * @return
    *   a [[Future]] which completes when the operation has completed with the result of the operation
    */
  def nop(tt: JsonTemplate): Future[Boolean]

  /** Closes the connection to the server. This operation is needed to correctly perform all clean up operations both in the
    * client and in the server, to dispose of all resources used. It is fundamental to call this method, otherwise the closing of
    * the application will lead to the server thinking that a disconnection occurred and it will retain all data about this
    * client, littering its memory with not freed up resources. A [[Future]] is still returned because the actual tuple space can
    * be hosted on a remote host, meaning that the operation is in fact a network operation that takes time to complete. The
    * future will complete signalling only the success of the operation.
    *
    * @return
    *   a [[Future]] which completes when the completion of the closing operation has occurred
    */
  def close(): Future[Unit]
}

/** Companion object to the [[JsonTupleSpace]] trait, containing its factory method. */
object JsonTupleSpace {

  /* Implementation of the JsonTupleSpace trait. */
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
    exitOnClose: Boolean,
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
      flowCompletion.flatMap(_ => if (exitOnClose) system.terminate().map(_ => ()) else Future.successful(()))
    }
  }

  /** Factory method for creating a new [[JsonTupleSpace]] client. It needs to be specified the URI of the host on which the
    * server is hosted and the client must connect to. The format of the URI should be the URL one and the protocol for connection
    * is the "websocket" one. Another optional parameter that can be specified is the size of the message buffer, which will be
    * used for buffering messages while the connection is still not established or while the connection is interrupted and the
    * client is trying to reconnect. As a "using" parameter an [[ActorSystem]] can be given if the default created one is not apt,
    * because the implementation will use an "Akka HTTP Client" under the hood which uses actors for handling requests and
    * responses. This method returns a [[Future]] signalling the completion of the connection operation to the server.
    *
    * @param uri
    *   the URI of the server to which this client should connect to
    * @param bufferSize
    *   the size of the buffer used for keeping messages while the client is not connected to the server
    * @param system
    *   the [[ActorSystem]] to be used for instantiating the "Akka HTTP Client" used under the hood
    * @return
    *   a [[Future]] which its completion signals the established connection to the server
    */
  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments", "scalafix:DisableSyntax.defaultArgs"))
  def apply(
    uri: String,
    bufferSize: Int = 1,
    exitOnClose: Boolean = true
  )(
    using
    system: ActorSystem = ActorSystem()
  ): Future[JsonTupleSpace] = {
    import system.dispatcher

    val completionPromise: Promise[Unit] = Promise[Unit]()
    val tupleSpace = JsonTupleSpaceImpl(uri, bufferSize, exitOnClose, completionPromise)
    completionPromise.future.map(_ => tupleSpace)
  }
}
