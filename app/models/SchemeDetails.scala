/*
 * Copyright 2020 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package models

import play.api.libs.json.JsArray
import play.api.libs.json.JsError
import play.api.libs.json.JsPath
import play.api.libs.json.JsResult
import play.api.libs.functional.syntax._
import play.api.libs.json.JsSuccess
import play.api.libs.json.Reads._
import play.api.libs.json.JsValue
import play.api.libs.json.Reads
import play.api.libs.json.Writes

import scala.annotation.tailrec

case class PspDetail(authorisingPsaId: String, pspId: String)

object PspDetail {
  val reads: Reads[PspDetail] = (
    (JsPath \ "authorisingPSAID").read[String] and
      (JsPath \ "id").read[String]
    )(
    (authorisingPsaId, pspId) => PspDetail(authorisingPsaId, pspId)
  )

  implicit val writes: Writes[PspDetail] =
    ((JsPath \ "authorisingPSAID").write[String] and
      (JsPath \ "id").write[String]).apply(pd => (pd.authorisingPsaId, pd.pspId))
}

case class SchemeDetails(schemeName: String, pstr: String, schemeStatus: String, pspDetails:Seq[PspDetail])

object SchemeDetails {
  def readsSeq[T](readsA: Reads[T]): Reads[Seq[T]] = new Reads[Seq[T]] {
    @tailrec
    private def readSeq(result: JsResult[Seq[T]], js: Seq[JsValue], reads: Reads[T]): JsResult[Seq[T]] = {
      js match {
        case Seq(h, t@_*) =>
          reads.reads(h) match {
            case JsSuccess(individual, _) => readSeq(JsSuccess(result.get :+ individual), t, reads)
            case error@JsError(_) => error
          }
        case Nil => result
      }
    }

    override def reads(json: JsValue): JsResult[Seq[T]] = {
      json match {
        case JsArray(members) => readSeq(JsSuccess(Nil), members, readsA)
        case _ => JsSuccess(Nil)
      }
    }
  }

  implicit val readsSeqPspDetail: Reads[Seq[PspDetail]] = readsSeq[PspDetail](PspDetail.reads)

  private val returnEmptyList: Reads[Seq[PspDetail]] =
    new Reads[Seq[PspDetail]] {override def reads(json: JsValue): JsResult[Seq[PspDetail]] = JsSuccess(Nil)}

  implicit val reads: Reads[SchemeDetails] =
    (
      (JsPath \ "schemeName").read[String] and
        (JsPath \ "pstr").read[String] and
        (JsPath \ "schemeStatus").read[String] and
        ((JsPath \ 'pspDetails ).read[Seq[PspDetail]] orElse returnEmptyList)
    )(
      (schemeName, pstr, status, pspDetails) => SchemeDetails(schemeName, pstr, status, pspDetails)
    )
}
