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

import io.circe.Decoder
import io.circe.DecodingFailure
import io.circe.Encoder
import io.circe.Json
import io.circe.syntax.*

import tuples.space.*
import tuples.space.JsonSerializable.given
import tuples.space.request.Request.MergeRequest
import AnyOps.*

object Serializers {

  given Encoder[TupleRequest] = r =>
    Json.obj(
      "content" -> r.content.asJson,
      "type" -> "out".asJson
    )

  given Decoder[TupleRequest] = c =>
    for {
      content <- c.downField("content").as[JsonTuple]
      - <- c
        .downField("type")
        .as[String]
        .filterOrElse(
          _ === "out",
          DecodingFailure(
            DecodingFailure.Reason.CustomReason("The value for the type field was not valid"),
            c.downField("type")
          )
        )
    } yield TupleRequest(content)

  given Encoder[SeqTupleRequest] = r =>
    Json.obj(
      "content" -> r.content.asJson,
      "type" -> "outAll".asJson
    )

  given Decoder[SeqTupleRequest] = c =>
    for {
      content <- c.downField("content").as[Seq[JsonTuple]]
      - <- c
        .downField("type")
        .as[String]
        .filterOrElse(
          _ === "outAll",
          DecodingFailure(
            DecodingFailure.Reason.CustomReason("The value for the type field was not valid"),
            c.downField("type")
          )
        )
    } yield SeqTupleRequest(content)

  given Encoder[TemplateRequest] = r =>
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

  given Decoder[TemplateRequest] = c =>
    for {
      content <- c.downField("content").as[JsonTemplate]
      tpe <- c.downField("type").as[String].flatMap {
        case "in" => Right[DecodingFailure, TemplateRequestType](TemplateRequestType.In)
        case "rd" => Right[DecodingFailure, TemplateRequestType](TemplateRequestType.Rd)
        case "no" => Right[DecodingFailure, TemplateRequestType](TemplateRequestType.No)
        case "inp" => Right[DecodingFailure, TemplateRequestType](TemplateRequestType.Inp)
        case "rdp" => Right[DecodingFailure, TemplateRequestType](TemplateRequestType.Rdp)
        case "nop" => Right[DecodingFailure, TemplateRequestType](TemplateRequestType.Nop)
        case "inAll" => Right[DecodingFailure, TemplateRequestType](TemplateRequestType.InAll)
        case "rdAll" => Right[DecodingFailure, TemplateRequestType](TemplateRequestType.RdAll)
        case _ =>
          Left[DecodingFailure, TemplateRequestType](
            DecodingFailure(
              DecodingFailure.Reason.CustomReason("The value for the type field was not valid"),
              c.downField("type")
            )
          )
      }
    } yield TemplateRequest(content, tpe)

  given Encoder[MergeRequest] = r =>
    Json.obj(
      "clientId" -> r.clientId.asJson,
      "oldClientId" -> r.oldClientId.asJson
    )

  given Decoder[MergeRequest] = Decoder.forProduct2("clientId", "oldClientId")(MergeRequest.apply)

  given Encoder[Request] = {
    case r: TupleRequest => r.asJson
    case r: TemplateRequest => r.asJson
    case r: SeqTupleRequest => r.asJson
    case r: MergeRequest => r.asJson
  }

  given Decoder[Request] = r =>
    r.as[TupleRequest]
      .orElse[DecodingFailure, Request](r.as[SeqTupleRequest])
      .orElse[DecodingFailure, Request](r.as[MergeRequest])
      .orElse[DecodingFailure, Request](r.as[TemplateRequest])
}
