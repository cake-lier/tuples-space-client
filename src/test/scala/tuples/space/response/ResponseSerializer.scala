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

import io.circe.Encoder
import io.circe.Json
import io.circe.syntax.EncoderOps

import tuples.space.JsonSerializable.given

object ResponseSerializer {

  given Encoder[TupleResponse] = r =>
    Json.obj(
      "request" -> r.request.asJson,
      "type" -> "out".asJson,
      "content" -> ().asJson
    )

  given Encoder[SeqTupleResponse] = r =>
    Json.obj(
      "request" -> r.request.asJson,
      "type" -> "outAll".asJson,
      "content" -> ().asJson
    )

  given Encoder[TemplateTupleResponse] = r =>
    Json.obj(
      "request" -> r.request.asJson,
      "type" -> (r.tpe match {
        case TemplateTupleResponseType.In => "in"
        case TemplateTupleResponseType.Rd => "rd"
      }).asJson,
      "content" -> r.content.asJson
    )

  given Encoder[TemplateMaybeTupleResponse] = r =>
    Json.obj(
      "request" -> r.request.asJson,
      "type" -> (r.tpe match {
        case TemplateMaybeTupleResponseType.Inp => "inp"
        case TemplateMaybeTupleResponseType.Rdp => "rdp"
      }).asJson,
      "content" -> r.content.asJson
    )

  given Encoder[TemplateSeqTupleResponse] = r =>
    Json.obj(
      "request" -> r.request.asJson,
      "type" -> (r.tpe match {
        case TemplateSeqTupleResponseType.InAll => "inAll"
        case TemplateSeqTupleResponseType.RdAll => "rdAll"
      }).asJson,
      "content" -> r.content.asJson
    )

  given Encoder[TemplateResponse] = r =>
    Json.obj(
      "request" -> r.request.asJson,
      "type" -> "no".asJson,
      "content" -> ().asJson
    )

  given Encoder[TemplateBooleanResponse] = r =>
    Json.obj(
      "request" -> r.request.asJson,
      "type" -> "nop".asJson,
      "content" -> r.content.asJson
    )

  given Encoder[ConnectionSuccessResponse] = r =>
    Json.obj(
      "clientId" -> r.clientId.asJson
    )

  given Encoder[MergeSuccessResponse] = r =>
    Json.obj(
      "newClientId" -> r.newClientId.asJson,
      "oldClientId" -> r.oldClientId.asJson
    )
}
