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
package tuples.space.response

import java.util.UUID

import tuples.space.*

/** A response that a [[io.github.cakelier.tuples.space.client.JsonTupleSpace]] can receive from its server.
  *
  * This trait represents a generic response that a [[io.github.cakelier.tuples.space.client.JsonTupleSpace]] client can receive
  * after sending a corresponding [[io.github.cakelier.tuples.space.request.Request]] to the server it has connected to. Responses
  * can be divided in two categories: proper responses and meta-responses. The first ones are used to carry data from the server
  * to the client, sometimes just the content of the corresponding request to signal that the operation completed with success.
  * Some other times, they have a proper content in addition, which represents the result of the operation. Their content can be a
  * single [[JsonTuple]], a [[Seq]] of them etc. Meta-responses are instead used for carrying metadata from the server to the
  * client, for example for signalling that the connection has been established with success and a new id is provided to the
  * client or that the client wants to use another id instead.
  */
sealed trait Response

/** Companion object to the [[Response]] trait, containing its implementations. */
object Response {

  /** A [[Response]] which associated request has as content a single [[JsonTuple]].
    *
    * This type of response is used for supporting "out" operations on the tuple space.
    */
  sealed trait TupleResponse extends Response {

      /** Returns the content of the request associated with this response, which is a single [[JsonTuple]]. */
    val request: JsonTuple
  }

  /** Companion object to the [[TupleResponse]] trait, containing its factory method. */
  object TupleResponse {

    /* Implementation of the TupleResponse trait. */
    final private case class TupleResponseImpl(request: JsonTuple) extends TupleResponse

      /** Creates a new instance of the [[TupleResponse]] trait, given the content of the associated request.
        *
        * @param request
        *   the content of the request associated to this response
        * @return
        *   a new instance of the [[TupleResponse]] trait
        */
    def apply(request: JsonTuple): TupleResponse = TupleResponseImpl(request)
  }

  /** A [[Response]] which associated request has as content a [[Seq]] of [[JsonTuple]]s.
    *
    * This type of response is used for supporting "outAll" operations on the tuple space.
    */
  sealed trait SeqTupleResponse extends Response {

      /** Returns the content of the request associated with this response, which is a [[Seq]] of [[JsonTuple]]s. */
    val request: Seq[JsonTuple]
  }

  /** Companion object to the [[SeqTupleResponse]] trait, containing its factory method. */
  object SeqTupleResponse {

    /* Implementation of the SeqTupleResponse trait. */
    final private case class SeqTupleResponseImpl(request: Seq[JsonTuple]) extends SeqTupleResponse

      /** Creates a new instance of the [[SeqTupleResponse]] trait, given the content of the associated request.
        *
        * @param request
        *   the content of the request associated to this response
        * @return
        *   a new instance of the [[SeqTupleResponse]] trait
        */
    def apply(request: Seq[JsonTuple]): SeqTupleResponse = SeqTupleResponseImpl(request)
  }

  /** A [[Response]] which associated request is a [[io.github.cakelier.tuples.space.request.Request.TemplateRequest]] one and
    * which content is a [[JsonTuple]].
    *
    * This type of response is used for supporting "in" and "rd" operations on the tuple space.
    */
  sealed trait TemplateTupleResponse extends Response {

      /** Returns the content of the request associated with this response, which is a [[JsonTemplate]]. */
    val request: JsonTemplate

    /** Returns the type of this response, chosen between the available types defined by [[TemplateTupleResponseType]]. */
    val tpe: TemplateTupleResponseType

    /** Returns the content of this response, which is a [[JsonTuple]]. */
    val content: JsonTuple
  }

  /** Companion object to the [[TemplateTupleResponse]] trait, containing its factory method. */
  object TemplateTupleResponse {

    /* Implementation of the TemplateTupleResponse trait. */
    final private case class TemplateTupleResponseImpl(request: JsonTemplate, tpe: TemplateTupleResponseType, content: JsonTuple)
      extends TemplateTupleResponse

      /** Creates a new instance of the [[TemplateTupleResponse]] trait, given the content of the associated request, the type of
        * this response and the content carried by this response.
        *
        * @param request
        *   the content of the request associated to this response
        * @param tpe
        *   the type of this response
        * @param content
        *   the content of this response
        * @return
        *   a new instance of the [[TemplateTupleResponse]] trait
        */
    def apply(request: JsonTemplate, tpe: TemplateTupleResponseType, content: JsonTuple): TemplateTupleResponse =
      TemplateTupleResponseImpl(request, tpe, content)
  }

  /** A [[Response]] which associated request is a [[io.github.cakelier.tuples.space.request.Request.TemplateRequest]] one and
    * which content is an [[Option]] of [[JsonTuple]].
    *
    * This type of response is used for supporting "inp" and "rdp" operations on the tuple space.
    */
  sealed trait TemplateMaybeTupleResponse extends Response {

      /** Returns the content of the request associated with this response, which is a [[JsonTemplate]]. */
    val request: JsonTemplate

    /** Returns the type of this response, chosen between the available types defined by [[TemplateMaybeTupleResponseType]]. */
    val tpe: TemplateMaybeTupleResponseType

    /** Returns the content of this response, which is an [[Option]] of [[JsonTuple]]. */
    val content: Option[JsonTuple]
  }

  /** Companion object to the [[TemplateMaybeTupleResponse]] trait, containing its factory method. */
  object TemplateMaybeTupleResponse {

    /* Implementation of the TemplateMaybeTupleResponse trait. */
    final private case class TemplateMaybeTupleResponseImpl(
      request: JsonTemplate,
      tpe: TemplateMaybeTupleResponseType,
      content: Option[JsonTuple]
    ) extends TemplateMaybeTupleResponse

      /** Creates a new instance of the [[TemplateMaybeTupleResponse]] trait, given the content of the associated request, the
        * type of this response and the content carried by this response.
        *
        * @param request
        *   the content of the request associated to this response
        * @param tpe
        *   the type of this response
        * @param content
        *   the content of this response
        * @return
        *   a new instance of the [[TemplateMaybeTupleResponse]] trait
        */
    def apply(
      request: JsonTemplate,
      tpe: TemplateMaybeTupleResponseType,
      content: Option[JsonTuple]
    ): TemplateMaybeTupleResponse =
      TemplateMaybeTupleResponseImpl(request, tpe, content)
  }

  /** A [[Response]] which associated request is a [[io.github.cakelier.tuples.space.request.Request.TemplateRequest]] one and
    * which content is a [[Seq]] of [[JsonTuple]].
    *
    * This type of response is used for supporting "inAll" and "rdAll" operations on the tuple space.
    */
  sealed trait TemplateSeqTupleResponse extends Response {

      /** Returns the content of the request associated with this response, which is a [[JsonTemplate]]. */
    val request: JsonTemplate

    /** Returns the type of this response, chosen between the available types defined by [[TemplateSeqTupleResponseType]]. */
    val tpe: TemplateSeqTupleResponseType

    /** Returns the content of this response, which is a [[Seq]] of [[JsonTuple]]. */
    val content: Seq[JsonTuple]
  }

  /** Companion object to the [[TemplateSeqTupleResponse]] trait, containing its factory method. */
  object TemplateSeqTupleResponse {

    /* Implementation of the TemplateSeqTupleResponse trait. */
    final private case class TemplateSeqTupleResponseImpl(
      request: JsonTemplate,
      tpe: TemplateSeqTupleResponseType,
      content: Seq[JsonTuple]
    ) extends TemplateSeqTupleResponse

      /** Creates a new instance of the [[TemplateSeqTupleResponse]] trait, given the content of the associated request, the type
        * of this response and the content carried by this response.
        *
        * @param request
        *   the content of the request associated to this response
        * @param tpe
        *   the type of this response
        * @param content
        *   the content of this response
        * @return
        *   a new instance of the [[TemplateSeqTupleResponse]] trait
        */
    def apply(request: JsonTemplate, tpe: TemplateSeqTupleResponseType, content: Seq[JsonTuple]): TemplateSeqTupleResponse =
      TemplateSeqTupleResponseImpl(request, tpe, content)
  }

  /** A [[Response]] which associated request has as content a single [[JsonTemplate]].
    *
    * This type of response is used for supporting "no" operations on the tuple space.
    */
  sealed trait TemplateResponse extends Response {

      /** Returns the content of the request associated with this response, which is a single [[JsonTemplate]]. */
    val request: JsonTemplate
  }

  /** Companion object to the [[TemplateResponse]] trait, containing its factory method. */
  object TemplateResponse {

    /* Implementation of the TemplateSeqTupleResponse trait. */
    final private case class TemplateResponseImpl(request: JsonTemplate) extends TemplateResponse

      /** Creates a new instance of the [[TemplateResponse]] trait, given the content of the associated request.
        *
        * @param request
        *   the content of the request associated to this response
        * @return
        *   a new instance of the [[TemplateResponse]] trait
        */
    def apply(request: JsonTemplate): TemplateResponse = TemplateResponseImpl(request)
  }

  /** A [[Response]] which associated request is a [[io.github.cakelier.tuples.space.request.Request.TemplateRequest]] one and
    * which content is a [[Boolean]].
    *
    * This type of response is used for supporting "nop" operations on the tuple space.
    */
  sealed trait TemplateBooleanResponse extends Response {

      /** Returns the content of the request associated with this response, which is a [[JsonTemplate]]. */
    val request: JsonTemplate

    /** Returns the content of this response, which is a [[Boolean]]. */
    val content: Boolean
  }

  /** Companion object to the [[TemplateBooleanResponse]] trait, containing its factory method. */
  object TemplateBooleanResponse {

    /* Implementation of the TemplateBooleanResponse trait. */
    final private case class TemplateBooleanResponseImpl(request: JsonTemplate, content: Boolean) extends TemplateBooleanResponse

      /** Creates a new instance of the [[TemplateBooleanResponse]] trait, given the content of the associated request and the
        * content carried by this response.
        *
        * @param request
        *   the content of the request associated to this response
        * @param content
        *   the content of this response
        * @return
        *   a new instance of the [[TemplateBooleanResponse]] trait
        */
    def apply(request: JsonTemplate, content: Boolean): TemplateBooleanResponse = TemplateBooleanResponseImpl(request, content)
  }

  /** The response sent from the server when the connection has been established with success.
    *
    * This response is used for two interlinked reasons: the first is clearly to signal the success in connecting to the server
    * from the client. The second is to give the client the id that will be associated with all its requests until disconnection.
    * The client has no need to put this id into its requests, the server will handle everything related to it. This is done in
    * case of a forced disconnection, where the client will have the opportunity, upon reconnection, to handle to the server its
    * old id and restore its normal course of operation.
    */
  sealed trait ConnectionSuccessResponse extends Response {

      /** Returns the client id of the client that receives this [[Response]]. */
    val clientId: UUID
  }

  /** Companion object to the [[ConnectionSuccessResponse]] trait, containing its factory method. */
  object ConnectionSuccessResponse {

    /* Implementation of the ConnectionSuccessResponse trait. */
    final private case class ConnectionSuccessResponseImpl(clientId: UUID) extends ConnectionSuccessResponse

      /** Creates a new instance of the [[ConnectionSuccessResponse]] trait, given the id of the client that receives this
        * [[ConnectionSuccessResponse]].
        *
        * @param clientId
        *   the id of the client that receives this [[ConnectionSuccessResponse]]
        * @return
        *   a new [[ConnectionSuccessResponse]] instance
        */
    def apply(clientId: UUID): ConnectionSuccessResponse = ConnectionSuccessResponseImpl(clientId)
  }

  /** A [[Response]] sent from the server for confirming the re-assignment to the client that sent the corresponding
    * [[io.github.cakelier.tuples.space.request.Request]] its old client id.
    *
    * When the connection to the server goes down, after the client reconnects, the server has no knowledge of whether this client
    * has already connected to it before or not. This is on purpose: it is always allowed for a client to purposefully connect to
    * the server, disconnect and then reconnect again at a later point in time. All pending operations from the client are lost,
    * because even if the client will reconnect, it would not be the same client from the server point of view. If the
    * disconnection happens abruptly followed by an error, the server can keep the pending operations, but it will never know if
    * the client will reappear or not. This [[Response]] is sent from the server to confirm just that: the server is telling that
    * a client previously connected has now reconnected and its id is now the one given in the previously sent
    * [[io.github.cakelier.tuples.space.request.Request]]. This way, the client can regain access to the
    * [[io.github.cakelier.tuples.space.response.Response]]s associated to the [[Request]]s placed before the disconnection.
    */
  sealed trait MergeSuccessResponse extends Response {

      /** Returns the client id of the client that sent the [[Request]] to change its previously current id into its old one. */
    val newClientId: UUID

    /** Returns the client id of the client that sent the [[Request]] to change its previously current id into its old one before
      * it was forced to disconnect.
      */
    val oldClientId: UUID
  }

  /** Companion object to the [[MergeSuccessResponse]] trait, containing its factory method. */
  object MergeSuccessResponse {

    /* Implementation of the MergeSuccessResponse trait. */
    final private case class MergeSuccessResponseImpl(newClientId: UUID, oldClientId: UUID) extends MergeSuccessResponse

      /** Creates a new instance of the [[MergeSuccessResponse]] trait, given the current id and the id before the disconnection
        * of the client that sent the [[MergeRequest]] associated with this [[MergeSuccessResponse]].
        *
        * @param newClientId
        *   the previously current id of the client that sent the associated [[MergeRequest]]
        * @param oldClientId
        *   the id of the client that sent the associated [[MergeRequest]] before it was forced to disconnect
        * @return
        *   a new [[MergeSuccessResponse]] instance
        */
    def apply(newClientId: UUID, oldClientId: UUID): MergeSuccessResponse = MergeSuccessResponseImpl(newClientId, oldClientId)
  }
}

export Response.*
