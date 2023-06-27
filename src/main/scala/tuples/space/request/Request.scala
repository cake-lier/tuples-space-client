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
package tuples.space.request

import java.util.UUID

import io.circe.Decoder
import io.circe.DecodingFailure
import io.circe.Encoder
import io.circe.Json
import io.circe.syntax.*

import tuples.space.*

sealed trait Request

object Request {

  sealed trait TupleRequest extends Request {

    val content: JsonTuple
  }

  object TupleRequest {

    final private case class TupleRequestImpl(content: JsonTuple) extends TupleRequest

    def apply(content: JsonTuple): TupleRequest = TupleRequestImpl(content)
  }

  sealed trait SeqTupleRequest extends Request {

    val content: Seq[JsonTuple]
  }

  object SeqTupleRequest {

    final private case class SeqTupleRequestImpl(content: Seq[JsonTuple]) extends SeqTupleRequest

    def apply(content: Seq[JsonTuple]): SeqTupleRequest = SeqTupleRequestImpl(content)
  }

  sealed trait TemplateRequest extends Request {

    val content: JsonTemplate

    val tpe: TemplateRequestType
  }

  object TemplateRequest {

    final private case class TemplateRequestImpl(content: JsonTemplate, tpe: TemplateRequestType) extends TemplateRequest

    def apply(content: JsonTemplate, tpe: TemplateRequestType): TemplateRequest = TemplateRequestImpl(content, tpe)
  }

  sealed trait MergeRequest extends Request {

    val clientId: UUID

    val oldClientId: UUID
  }

  object MergeRequest {

    final private case class MergeRequestImpl(clientId: UUID, oldClientId: UUID) extends MergeRequest

    def apply(clientId: UUID, oldClientId: UUID): MergeRequest = MergeRequestImpl(clientId, oldClientId)
  }
}

export Request.*
