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

sealed trait Response

object Response {

  sealed trait TupleResponse extends Response {

    val request: JsonTuple
  }

  object TupleResponse {

    final private case class TupleResponseImpl(request: JsonTuple) extends TupleResponse

    def apply(request: JsonTuple): TupleResponse = TupleResponseImpl(request)
  }

  sealed trait SeqTupleResponse extends Response {

    val request: Seq[JsonTuple]
  }

  object SeqTupleResponse {

    final private case class SeqTupleResponseImpl(request: Seq[JsonTuple]) extends SeqTupleResponse

    def apply(request: Seq[JsonTuple]): SeqTupleResponse = SeqTupleResponseImpl(request)
  }

  sealed trait TemplateTupleResponse extends Response {

    val request: JsonTemplate

    val tpe: TemplateTupleResponseType

    val content: JsonTuple
  }

  object TemplateTupleResponse {

    final private case class TemplateTupleResponseImpl(request: JsonTemplate, tpe: TemplateTupleResponseType, content: JsonTuple)
      extends TemplateTupleResponse

    def apply(request: JsonTemplate, tpe: TemplateTupleResponseType, content: JsonTuple): TemplateTupleResponse =
      TemplateTupleResponseImpl(request, tpe, content)
  }

  sealed trait TemplateMaybeTupleResponse extends Response {

    val request: JsonTemplate

    val tpe: TemplateMaybeTupleResponseType

    val content: Option[JsonTuple]
  }

  object TemplateMaybeTupleResponse {

    final private case class TemplateMaybeTupleResponseImpl(
      request: JsonTemplate,
      tpe: TemplateMaybeTupleResponseType,
      content: Option[JsonTuple]
    ) extends TemplateMaybeTupleResponse

    def apply(
      request: JsonTemplate,
      tpe: TemplateMaybeTupleResponseType,
      content: Option[JsonTuple]
    ): TemplateMaybeTupleResponse =
      TemplateMaybeTupleResponseImpl(request, tpe, content)
  }

  sealed trait TemplateSeqTupleResponse extends Response {

    val request: JsonTemplate

    val tpe: TemplateSeqTupleResponseType

    val content: Seq[JsonTuple]
  }

  object TemplateSeqTupleResponse {

    final private case class TemplateSeqTupleResponseImpl(
      request: JsonTemplate,
      tpe: TemplateSeqTupleResponseType,
      content: Seq[JsonTuple]
    ) extends TemplateSeqTupleResponse

    def apply(request: JsonTemplate, tpe: TemplateSeqTupleResponseType, content: Seq[JsonTuple]): TemplateSeqTupleResponse =
      TemplateSeqTupleResponseImpl(request, tpe, content)
  }

  sealed trait TemplateResponse extends Response {

    val request: JsonTemplate
  }

  object TemplateResponse {

    final private case class TemplateResponseImpl(request: JsonTemplate) extends TemplateResponse

    def apply(request: JsonTemplate): TemplateResponse = TemplateResponseImpl(request)
  }

  sealed trait TemplateBooleanResponse extends Response {

    val request: JsonTemplate

    val content: Boolean
  }

  object TemplateBooleanResponse {

    final private case class TemplateBooleanResponseImpl(request: JsonTemplate, content: Boolean) extends TemplateBooleanResponse

    def apply(request: JsonTemplate, content: Boolean): TemplateBooleanResponse = TemplateBooleanResponseImpl(request, content)
  }

  sealed trait ConnectionSuccessResponse extends Response {

    val clientId: UUID
  }

  object ConnectionSuccessResponse {

    final private case class ConnectionSuccessResponseImpl(clientId: UUID) extends ConnectionSuccessResponse

    def apply(clientId: UUID): ConnectionSuccessResponse = ConnectionSuccessResponseImpl(clientId)
  }

  sealed trait MergeSuccessResponse extends Response {

    val newClientId: UUID

    val oldClientId: UUID
  }

  object MergeSuccessResponse {

    final private case class MergeSuccessResponseImpl(newClientId: UUID, oldClientId: UUID) extends MergeSuccessResponse

    def apply(newClientId: UUID, oldClientId: UUID): MergeSuccessResponse = MergeSuccessResponseImpl(newClientId, oldClientId)
  }
}

export Response.*
