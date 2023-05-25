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

package connectors

import com.google.inject.Inject
import config.FrontendAppConfig
import models.{AFTOverview, JourneyType, Quarters, UserAnswers, VersionsWithSubmitter}
import play.api.Logger
import play.api.http.Status._
import play.api.libs.json._
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse}
import utils.{DateHelper, HttpResponseHelper}

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Failure

class AFTConnector @Inject()(http: HttpClient, config: FrontendAppConfig)
  extends HttpResponseHelper {

  private val logger = Logger(classOf[AFTConnector])

  def fileAFTReturn(pstr: String, answers: UserAnswers, journeyType: JourneyType.Name)
                   (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Unit] = {
    val url = config.aftFileReturn.format(journeyType.toString)
    val aftHc = hc.withExtraHeaders(headers = "pstr" -> pstr)
    http.POST[JsObject, HttpResponse](url, answers.data)(implicitly, implicitly, aftHc, implicitly).map {
      response =>
        response.status match {
          case OK => ()
            //TODO:7967 add a new case for no content >> throw a specific exception
          case FORBIDDEN  if response.body.contains("RETURN_ALREADY_SUBMITTED") =>
            throw ReturnAlreadySubmittedException()
          case _ => handleErrorResponse("POST", url)(response)
        }
    } andThen {
      case Failure(t: Throwable) => logger.warn("Unable to post aft return", t)
    }
  }

  def getAFTDetails(pstr: String, startDate: String, aftVersion: String)
                   (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[JsValue] = {
    val url = config.getAftDetails
    val aftHc = hc.withExtraHeaders(headers = "pstr" -> pstr, "startDate" -> startDate, "aftVersion" -> aftVersion)
    logger.warn(s"Calling getAFT details")
    http.GET[HttpResponse](url)(implicitly, aftHc, implicitly).map { response =>
      logger.warn(s"GetAFT details resturned response with status ${response.status}")
      response.status match {
        case OK => Json.parse(response.body)
        case _ => handleErrorResponse("GET", url)(response)
      }
    } andThen {
      case Failure(t: Throwable) => logger.warn("Unable to get aft details", t)
    }
  }

  def getIsAftNonZero(pstr: String, startDate: String, aftVersion: String)
                     (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Boolean] = {
    val url = config.isAftNonZero
    val aftHc = hc.withExtraHeaders(headers = "pstr" -> pstr, "startDate" -> startDate, "aftVersion" -> aftVersion)
    http.GET[HttpResponse](url)(implicitly, aftHc, implicitly).map { response =>
      response.status match {
        case OK => response.json.as[Boolean]
        case _ => handleErrorResponse("GET", url)(response)
      }
    }
  }

  def getListOfVersions(pstr: String, startDate: String)
                       (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[VersionsWithSubmitter]] = {
    val url = config.aftListOfVersions
    val schemeHc = hc.withExtraHeaders("pstr" -> pstr, "startDate" -> startDate)
    http.GET[HttpResponse](url)(implicitly, schemeHc, implicitly).map { response =>
      response.status match {
        case OK =>
          Json.parse(response.body).validate[Seq[VersionsWithSubmitter]] match {
            case JsSuccess(value, _) => value
            case JsError(errors) => throw JsResultException(errors)
          }
        case NOT_FOUND =>
          Seq.empty
        case _ =>
          handleErrorResponse("GET", url)(response)
      }
    } andThen {
      case Failure(t: Throwable) => logger.warn("Unable to get list of versions", t)
    }
  }

  def getAftOverview(pstr: String, startDate: Option[String] = None, endDate: Option[String] = None)
                    (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[AFTOverview]] = {
    val url = config.aftOverviewUrl
    val schemeHc = hc.withExtraHeaders(
      "pstr" -> pstr,
      "startDate" -> aftOverviewStartDate.toString,
      "endDate" -> aftOverviewEndDate.toString
    )

    http.GET[HttpResponse](url)(implicitly, schemeHc, implicitly).map { response =>
      response.status match {
        case OK =>
          Json.parse(response.body).validate[Seq[AFTOverview]] match {
            case JsSuccess(value, _) => filterOverviewResponse(startDate, endDate, value)
            case JsError(errors) => throw JsResultException(errors)
          }
        case _ =>
          handleErrorResponse("GET", url)(response)
      }
    } andThen {
      case Failure(t: Throwable) => logger.warn("Unable to get aft overview", t)
    }
  }

   def filterOverviewResponse(startDate: Option[String], endDate: Option[String], seqOverview: Seq[AFTOverview]): Seq[AFTOverview] = {
    val startDt = startDate.fold(aftOverviewStartDate)(LocalDate.parse)
    val endDt = endDate.fold(aftOverviewEndDate)(LocalDate.parse)
    seqOverview.filterNot(item => item.periodStartDate.isBefore(startDt) || item.periodEndDate.isAfter(endDt))
  }

  def aftOverviewEndDate: LocalDate = Quarters.getQuarter(DateHelper.today).endDate

  def aftOverviewStartDate: LocalDate = {
    val earliestStartDate = LocalDate.parse(config.earliestStartDate)
    val calculatedStartYear = aftOverviewEndDate.minusYears(config.aftNoOfYearsDisplayed).getYear
    val calculatedStartDate = LocalDate.of(calculatedStartYear, 1, 1)

    if (calculatedStartDate.isAfter(earliestStartDate)) calculatedStartDate else earliestStartDate
  }
}

case class ReturnAlreadySubmittedException() extends Exception
