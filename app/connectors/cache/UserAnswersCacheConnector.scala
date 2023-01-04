/*
 * Copyright 2023 HM Revenue & Customs
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

package connectors.cache

import com.google.inject.Inject
import config.FrontendAppConfig
import models.{ChargeType, SessionAccessData, LockDetail, SessionData}
import play.api.http.Status._
import play.api.libs.json._
import play.api.mvc.Result
import play.api.mvc.Results._
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http._

import scala.concurrent.{ExecutionContext, Future}

class UserAnswersCacheConnectorImpl @Inject()(
                                               config: FrontendAppConfig,
                                               http: HttpClient
                                             ) extends UserAnswersCacheConnector {

  override protected def saveUrl = s"${config.aftUrl}/pension-scheme-accounting-for-tax/journey-cache/aft"

  override protected def saveSessionUrl = s"${config.aftUrl}/pension-scheme-accounting-for-tax/journey-cache/aft/session-data"

  override protected def saveSessionAndLockUrl = s"${config.aftUrl}/pension-scheme-accounting-for-tax/journey-cache/aft/session-data-lock"

  override protected def lockDetailUrl = s"${config.aftUrl}/pension-scheme-accounting-for-tax/journey-cache/aft/lock"

    override def fetch(id: String)
    (implicit ec: ExecutionContext, headerCarrier: HeaderCarrier): Future[Option[JsValue]] = {

    val headers: Seq[(String, String)] = Seq(("Content-Type", "application/json"), ("id", id))
    val hc: HeaderCarrier = headerCarrier.withExtraHeaders(headers: _*)

    http.GET[HttpResponse](saveUrl)(implicitly, hc, implicitly)
      .recoverWith(mapExceptionsToStatus)
      .map { response =>
        response.status match {
          case NOT_FOUND =>
            None
          case OK =>
            Some(Json.parse(response.body))
          case _ =>
            throw new HttpException(response.body, response.status)
        }
      }
  }

  def save(id: String, value: JsValue)
          (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[JsValue] = {

    val allExtraHeaders = Seq(
      Tuple2("id", id), Tuple2("content-type", "application/json")
    )

    savePost(allExtraHeaders, saveUrl, value)
  }

  def savePartial(
    id: String,
    value: JsValue,
    chargeType:Option[ChargeType] = None,
    memberNo:Option[Int] = None
  )(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[JsValue] = {
    val memberNoHeader = memberNo match {
      case Some(mn) => Seq(Tuple2("memberNo", (mn + 1).toString))
      case _ => Nil
    }

    val chargeTypeHeader = chargeType match {
      case Some(ct) => Seq(Tuple2("chargeType", ct.toString))
      case _ => Seq(Tuple2("chargeType", "none"))
    }

    val allExtraHeaders = Seq(
      Tuple2("id", id),
      Tuple2("content-type", "application/json")
    ) ++ chargeTypeHeader ++ memberNoHeader
    savePost(allExtraHeaders, saveUrl, value)
  }

  private def savePost(headers: Seq[(String, String)], url: String, value: JsValue)
    (implicit ec: ExecutionContext, headerCarrier: HeaderCarrier): Future[JsValue] = {
    val hc: HeaderCarrier = headerCarrier.withExtraHeaders(headers: _*)

    http.POST[JsValue, HttpResponse](url, value)(implicitly, implicitly, hc, implicitly)
      .map { response =>
        response.status match {
          case CREATED =>
            value
          case _ =>
            throw new HttpException(response.body, response.status)
        }
      }
  }

  override def saveAndLock(id: String, value: JsValue, sessionAccessData: SessionAccessData, lockReturn: Boolean = false)
                          (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[JsValue] = {
    val useURL = if (lockReturn) saveSessionAndLockUrl else saveSessionUrl

    val sessionDataHeaders = Seq(
      Tuple2("version", sessionAccessData.version.toString),
      Tuple2("accessMode", sessionAccessData.accessMode.toString),
      Tuple2("areSubmittedVersionsAvailable", sessionAccessData.areSubmittedVersionsAvailable.toString))
    val allExtraHeaders = Seq(Tuple2("id", id), Tuple2("content-type", "application/json")) ++ sessionDataHeaders

    savePost(allExtraHeaders, useURL, value)
  }

  override def removeAll(id: String)
    (implicit ec: ExecutionContext, headerCarrier: HeaderCarrier): Future[Result] = {
    val headers: Seq[(String, String)] = Seq(("id", id))
    val hc: HeaderCarrier = headerCarrier.withExtraHeaders(headers: _*)
    http.DELETE[HttpResponse](saveUrl)(implicitly, hc, implicitly).map { _ =>
      Ok
    }
  }

  override def getSessionData(id: String)
    (implicit ec: ExecutionContext, headerCarrier: HeaderCarrier): Future[Option[SessionData]] = {
    val headers: Seq[(String, String)] = Seq(("Content-Type", "application/json"), ("id", id))
    val hc: HeaderCarrier = headerCarrier.withExtraHeaders(headers: _*)

    http.GET[HttpResponse](saveSessionUrl)(implicitly, hc, implicitly)
      .recoverWith(mapExceptionsToStatus)
      .map { response =>
        response.status match {
          case NOT_FOUND => None
          case OK =>
            val sessionData = Json.parse(response.body).validate[SessionData] match {
              case JsSuccess(value, _) => value
              case JsError(errors) => throw JsResultException(errors)
            }
            Some(sessionData)
          case _ => throw new HttpException(response.body, response.status)
        }
      }
  }

  override def lockDetail(srn: String, startDate: String)
    (implicit ec: ExecutionContext, headerCarrier: HeaderCarrier): Future[Option[LockDetail]] = {

    val headers: Seq[(String, String)] = Seq(("Content-Type", "application/json"), ("id", srn + startDate))
    val hc: HeaderCarrier = headerCarrier.withExtraHeaders(headers: _*)

    http.GET[HttpResponse](lockDetailUrl)(implicitly, hc, implicitly)
      .recoverWith(mapExceptionsToStatus)
      .map { response =>
        response.status match {
          case NOT_FOUND =>
            None
          case OK =>
            Some(Json.parse(response.body).as[LockDetail])
          case _ =>
            throw new HttpException(response.body, response.status)
        }
      }
  }

  private def mapExceptionsToStatus: PartialFunction[Throwable, Future[HttpResponse]] = {
    case _: NotFoundException =>
      Future.successful(HttpResponse(NOT_FOUND, "Not found"))
  }
}

trait UserAnswersCacheConnector {

  protected def saveUrl: String

  protected def saveSessionUrl: String

  protected def saveSessionAndLockUrl: String

  protected def lockDetailUrl: String

  def fetch(cacheId: String)
           (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Option[JsValue]]

  def save(cacheId: String, value: JsValue)
          (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[JsValue]

  def savePartial(id: String, value: JsValue, chargeType:Option[ChargeType] = None, memberNo:Option[Int] = None)
    (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[JsValue]

  def removeAll(cacheId: String)
               (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Result]

  def getSessionData(id: String)
                    (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Option[SessionData]]

  def lockDetail(srn: String, startDate: String)
                (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Option[LockDetail]]

  def saveAndLock(id: String, value: JsValue, sessionAccessData: SessionAccessData, lockReturn: Boolean = false)
                 (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[JsValue]

}
