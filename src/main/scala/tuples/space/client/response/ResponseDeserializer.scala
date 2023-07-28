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
package tuples.space.client.response

import io.circe.Decoder
import io.circe.DecodingFailure
import io.circe.syntax.*

import AnyOps.*
import tuples.space.*
import tuples.space.JsonSerializable.given

/** This object contains all deserializers for the [[Response]] sub-types. */
private[client] object ResponseDeserializer {

  /* The Decoder given instance for the TupleResponse trait. */
  private given Decoder[TupleResponse] = c =>
    for {
      request <- c.downField("request").as[JsonTuple]
      _ <- c
        .downField("type")
        .as[String]
        .filterOrElse(
          _ === "out",
          DecodingFailure(
            DecodingFailure.Reason.CustomReason("The value for the type field was not valid"),
            c.downField("type")
          )
        )
      _ <- c.downField("content").as[Unit]
    } yield TupleResponse(request)

    /* The Decoder given instance for the SeqTupleResponse trait. */
  private given Decoder[SeqTupleResponse] = c =>
    for {
      request <- c.downField("request").as[Seq[JsonTuple]]
      _ <- c
        .downField("type")
        .as[String]
        .filterOrElse(
          _ === "outAll",
          DecodingFailure(
            DecodingFailure.Reason.CustomReason("The value for the type field was not valid"),
            c.downField("type")
          )
        )
      _ <- c.downField("content").as[Unit]
    } yield SeqTupleResponse(request)

    /* The Decoder given instance for the TemplateTupleResponse trait. */
  private given Decoder[TemplateTupleResponse] = c =>
    for {
      request <- c.downField("request").as[JsonTemplate]
      tpe <- c.downField("type").as[String].flatMap {
        case "in" => Right[DecodingFailure, TemplateTupleResponseType](TemplateTupleResponseType.In)
        case "rd" => Right[DecodingFailure, TemplateTupleResponseType](TemplateTupleResponseType.Rd)
        case _ =>
          Left[DecodingFailure, TemplateTupleResponseType](
            DecodingFailure(
              DecodingFailure.Reason.CustomReason("The value for the type field was not valid"),
              c.downField("type")
            )
          )
      }
      content <- c.downField("content").as[JsonTuple]
    } yield TemplateTupleResponse(request, tpe, content)

    /* The Decoder given instance for the TemplateMaybeTupleResponse trait. */
  private given Decoder[TemplateMaybeTupleResponse] = c =>
    for {
      request <- c.downField("request").as[JsonTemplate]
      tpe <- c.downField("type").as[String].flatMap {
        case "inp" => Right[DecodingFailure, TemplateMaybeTupleResponseType](TemplateMaybeTupleResponseType.Inp)
        case "rdp" => Right[DecodingFailure, TemplateMaybeTupleResponseType](TemplateMaybeTupleResponseType.Rdp)
        case _ =>
          Left[DecodingFailure, TemplateMaybeTupleResponseType](
            DecodingFailure(
              DecodingFailure.Reason.CustomReason("The value for the type field was not valid"),
              c.downField("type")
            )
          )
      }
      content <- c.downField("content").as[Option[JsonTuple]]
    } yield TemplateMaybeTupleResponse(request, tpe, content)

    /* The Decoder given instance for the TemplateSeqTupleResponse trait. */
  private given Decoder[TemplateSeqTupleResponse] = c =>
    for {
      request <- c.downField("request").as[JsonTemplate]
      tpe <- c.downField("type").as[String].flatMap {
        case "inAll" => Right[DecodingFailure, TemplateSeqTupleResponseType](TemplateSeqTupleResponseType.InAll)
        case "rdAll" => Right[DecodingFailure, TemplateSeqTupleResponseType](TemplateSeqTupleResponseType.RdAll)
        case _ =>
          Left[DecodingFailure, TemplateSeqTupleResponseType](
            DecodingFailure(
              DecodingFailure.Reason.CustomReason("The value for the type field was not valid"),
              c.downField("type")
            )
          )
      }
      content <- c.downField("content").as[Seq[JsonTuple]]
    } yield TemplateSeqTupleResponse(request, tpe, content)

    /* The Decoder given instance for the TemplateResponse trait. */
  private given Decoder[TemplateResponse] = c =>
    for {
      request <- c.downField("request").as[JsonTemplate]
      _ <- c
        .downField("type")
        .as[String]
        .filterOrElse(
          _ === "no",
          DecodingFailure(
            DecodingFailure.Reason.CustomReason("The value for the type field was not valid"),
            c.downField("type")
          )
        )
      _ <- c.downField("content").as[Unit]
    } yield TemplateResponse(request)

    /* The Decoder given instance for the TemplateBooleanResponse trait. */
  private given Decoder[TemplateBooleanResponse] = c =>
    for {
      request <- c.downField("request").as[JsonTemplate]
      _ <- c
        .downField("type")
        .as[String]
        .filterOrElse(
          _ === "nop",
          DecodingFailure(
            DecodingFailure.Reason.CustomReason("The value for the type field was not valid"),
            c.downField("type")
          )
        )
      content <- c.downField("content").as[Boolean]
    } yield TemplateBooleanResponse(request, content)

    /* The Decoder given instance for the ConnectionSuccessResponse trait. */
  private given Decoder[ConnectionSuccessResponse] = Decoder.forProduct1("clientId")(ConnectionSuccessResponse.apply)

  /* The Decoder given instance for the MergeSuccessResponse trait. */
  private given Decoder[MergeSuccessResponse] =
    Decoder.forProduct2("newClientId", "oldClientId")(MergeSuccessResponse.apply)

    /** The [[Decoder]] given instance for the general [[Response]] trait, working for all of its sub-types. */
  given Decoder[Response] = r =>
    r.as[TupleResponse]
      .orElse(r.as[SeqTupleResponse])
      .orElse(r.as[TemplateResponse])
      .orElse(r.as[TemplateBooleanResponse])
      .orElse(r.as[TemplateMaybeTupleResponse])
      .orElse(r.as[TemplateSeqTupleResponse])
      .orElse(r.as[TemplateTupleResponse])
      .orElse(r.as[ConnectionSuccessResponse])
      .orElse(r.as[MergeSuccessResponse])
}
