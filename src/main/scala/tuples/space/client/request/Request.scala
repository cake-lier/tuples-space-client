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
package tuples.space.client.request

import java.util.UUID

import io.circe.Decoder
import io.circe.DecodingFailure
import io.circe.Encoder
import io.circe.Json
import io.circe.syntax.*

import tuples.space.*

/** A request that a [[io.github.cakelier.tuples.space.client.JsonTupleSpace]] can make to its server.
  *
  * This trait represents a generic request that a [[io.github.cakelier.tuples.space.client.JsonTupleSpace]] client can make, in
  * order to obtain a [[io.github.cakelier.tuples.space.response.Response]] from the server it has connected to. Requests can be
  * divided in two categories: proper requests and meta-requests. The first ones are used to carry data from the client to the
  * server and they always have a content, which can be a single [[JsonTuple]], a [[Seq]] of them etc. Meta-requests are instead
  * used for carrying metadata from the client to the server, for example for managing the [[UUID]] that the server has given to
  * the client.
  */
sealed private[client] trait Request

/** Companion object to the [[Request]] trait, containing its implementations. */
private[client] object Request {

  /** A [[Request]] which content is a single [[JsonTuple]].
    *
    * This type of request is used for supporting "out" operations on the tuple space.
    */
  sealed trait TupleRequest extends Request {

      /** Returns the content of this request, which is a single [[JsonTuple]]. */
    val content: JsonTuple
  }

  /** Companion object to the [[TupleRequest]] trait, containing its factory method. */
  object TupleRequest {

    /* Implementation of the TupleRequest trait. */
    final private case class TupleRequestImpl(content: JsonTuple) extends TupleRequest

      /** Creates a new instance of the [[TupleRequest]] trait, given the content of the request.
        *
        * @param content
        *   the [[JsonTuple]] that makes the content of the request
        * @return
        *   a new [[TupleRequest]] instance
        */
    def apply(content: JsonTuple): TupleRequest = TupleRequestImpl(content)
  }

  /** A [[Request]] which content is a [[Seq]] of [[JsonTuple]]s.
    *
    * This type of request is used for supporting "outAll" operations on the tuple space.
    */
  sealed trait SeqTupleRequest extends Request {

      /** Returns the content of this request, which is a [[Seq]] of [[JsonTuple]]s. */
    val content: Seq[JsonTuple]
  }

  /** Companion object to the [[SeqTupleRequest]] trait, containing its factory method. */
  object SeqTupleRequest {

    /* Implementation of the SeqTupleRequest trait. */
    final private case class SeqTupleRequestImpl(content: Seq[JsonTuple]) extends SeqTupleRequest

      /** Creates a new instance of the [[SeqTupleRequest]] trait, given the content of the request.
        *
        * @param content
        *   the [[Seq]] of [[JsonTuple]]s that makes the content of the request
        * @return
        *   a new [[SeqTupleRequest]] instance
        */
    def apply(content: Seq[JsonTuple]): SeqTupleRequest = SeqTupleRequestImpl(content)
  }

  /** A [[Request]] which content is a [[JsonTemplate]].
    *
    * This type of request is used for supporting "in", "rd", "no", "inp", "rdp", "nop", "inAll" and "rdAll" operations on the
    * tuple space.
    */
  sealed trait TemplateRequest extends Request {

      /** Returns the content of this request, which is a [[JsonTemplate]]. */
    val content: JsonTemplate

    /** Returns the type of this request, chosen between the available types defined by [[TemplateRequestType]]. */
    val tpe: TemplateRequestType
  }

  /** Companion object to the [[TemplateRequest]] trait, containing its factory method. */
  object TemplateRequest {

    /* Implementation of the TemplateRequest trait. */
    final private case class TemplateRequestImpl(content: JsonTemplate, tpe: TemplateRequestType) extends TemplateRequest

      /** Creates a new instance of the [[TemplateRequest]] trait, given the content and the type of the request.
        *
        * @param content
        *   the [[JsonTemplate]] that makes the content of the request
        * @param tpe
        *   the type of the request
        * @return
        *   a new [[TemplateRequest]] instance
        */
    def apply(content: JsonTemplate, tpe: TemplateRequestType): TemplateRequest = TemplateRequestImpl(content, tpe)
  }

  /** A [[Request]] to be sent to the server for re-assigning to itself its old client id.
    *
    * When the connection to the server goes down, after the client reconnects, the server has no knowledge of whether this client
    * has already connected to it before or not. This is on purpose: it is always allowed for a client to purposefully connect to
    * the server, disconnect and then reconnect again at a later point in time. All pending operations from the client are lost,
    * because even if the client will reconnect, it would not be the same client from the server point of view. If the
    * disconnection happens abruptly followed by an error, the server can keep the pending operations, but it will never know if
    * the client will reappear or not. This [[Request]] is needed to do just that, to tell the server that a client previously
    * connected has now reconnected and its old id was the one given. This way, it can regain access to the
    * [[io.github.cakelier.tuples.space.response.Response]]s associated to the [[Request]]s placed before the disconnection.
    */
  sealed trait MergeRequest extends Request {

      /** Returns the current client id of the client that sent this [[Request]]. */
    val clientId: UUID

    /** Returns the client id of the client that sent this [[Request]] before it was forced to disconnect. */
    val oldClientId: UUID
  }

  /** Companion object to the [[MergeRequest]] trait, containing its factory method. */
  object MergeRequest {

    /* Implementation of the MergeRequest trait. */
    final private case class MergeRequestImpl(clientId: UUID, oldClientId: UUID) extends MergeRequest

      /** Creates a new instance of the [[MergeRequest]] trait, given the current id and the id before the disconnection of the
        * client that sent this [[MergeRequest]].
        *
        * @param clientId
        *   the current id of the client that sent this [[MergeRequest]]
        * @param oldClientId
        *   the id of the client that sent this [[MergeRequest]] before it was forced to disconnect
        * @return
        *   a new [[MergeRequest]] instance
        */
    def apply(clientId: UUID, oldClientId: UUID): MergeRequest = MergeRequestImpl(clientId, oldClientId)
  }
}

export Request.*
