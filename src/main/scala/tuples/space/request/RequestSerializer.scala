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

import io.circe.Encoder
import io.circe.Json
import io.circe.syntax.*

import tuples.space.*
import tuples.space.JsonSerializable.given
import AnyOps.*

/** This object contains all serializers for the [[Request]] sub-types. */
object RequestSerializer {

  /* The Encoder given instance for the TupleRequest trait. */
  private given Encoder[TupleRequest] = r =>
    Json.obj(
      "content" -> r.content.asJson,
      "type" -> "out".asJson
    )

    /* The Encoder given instance for the SeqTupleRequest trait. */
  private given Encoder[SeqTupleRequest] = r =>
    Json.obj(
      "content" -> r.content.asJson,
      "type" -> "outAll".asJson
    )

    /* The Encoder given instance for the TemplateRequest trait. */
  private given Encoder[TemplateRequest] = r =>
    Json.obj(
      "content" -> r.content.asJson,
      "type" -> (r.tpe match {
        case TemplateRequestType.In => "in"
        case TemplateRequestType.Rd => "rd"
        case TemplateRequestType.No => "no"
        case TemplateRequestType.Inp => "inp"
        case TemplateRequestType.Rdp => "rdp"
        case TemplateRequestType.Nop => "nop"
        case TemplateRequestType.InAll => "inAll"
        case TemplateRequestType.RdAll => "rdAll"
      }).asJson
    )

    /* The Encoder given instance for the MergeRequest trait. */
  private given Encoder[MergeRequest] = r =>
    Json.obj(
      "clientId" -> r.clientId.asJson,
      "oldClientId" -> r.oldClientId.asJson
    )

    /** The [[Encoder]] given instance for the general [[Request]] trait, working for all of its sub-types. */
  given Encoder[Request] = {
    case r: TupleRequest => r.asJson
    case r: TemplateRequest => r.asJson
    case r: SeqTupleRequest => r.asJson
    case r: MergeRequest => r.asJson
  }
}
