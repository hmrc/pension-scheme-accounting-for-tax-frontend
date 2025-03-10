/*
 * Copyright 2024 HM Revenue & Customs
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
import org.apache.pekko.http.scaladsl.model.Uri
import play.api.Logger
import play.api.http.Status._
import play.api.libs.json._
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}
import utils.{DateHelper, HttpResponseHelper}

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Failure

class AFTConnector @Inject()(httpClient2: HttpClientV2, config: FrontendAppConfig)
  extends HttpResponseHelper {

  private val logger = Logger(classOf[AFTConnector])

  def fileAFTReturn(pstr: String, answers: UserAnswers, journeyType: JourneyType.Name,
                    srn: String, loggedInAsPsa:Boolean)
                   (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Unit] = {
    val url = url"${Uri(config.aftFileReturn.format(journeyType.toString, srn))
                  .withQuery(Uri.Query("loggedInAsPsa" -> s"$loggedInAsPsa"))}"
    val headers: Seq[(String, String)] = Seq("pstr" -> pstr)
    val aftHc = hc.withExtraHeaders(headers = headers:_*)

    httpClient2
      .post(url)(aftHc)
      .setHeader(headers: _*)
      .transform(_.withRequestTimeout(config.ifsTimeout))
      .withBody(answers.data)
      .execute[HttpResponse].map {
      response =>
        response.status match {
          case OK => ()
          case NO_CONTENT => throw ReturnAlreadySubmittedException()
          case FORBIDDEN  if response.body.contains("RETURN_ALREADY_SUBMITTED") =>
            throw ReturnAlreadySubmittedException()
          case _ => handleErrorResponse("POST", url.toString)(response)
        }
    } andThen {
      case Failure(_: ReturnAlreadySubmittedException) => ()
      case Failure(t: Throwable) => logger.warn("Unable to post aft return", t)
    }
  }

  def getAFTDetails(pstr: String, startDate: String, aftVersion: String, srn: String, loggedInAsPsa: Boolean)
                   (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[JsValue] = {
    val url = url"${Uri(config.getAftDetails.format(srn))
                .withQuery(Uri.Query("loggedInAsPsa" -> s"$loggedInAsPsa"))}"
    val headers: Seq[(String, String)] = Seq("pstr" -> pstr, "startDate" -> startDate, "aftVersion" -> aftVersion)
    val aftHc = hc.withExtraHeaders(headers = headers:_*)
    logger.info("Calling getAFT details")
    httpClient2
      .get(url)(aftHc)
      .setHeader(headers :_*)
      .transform(_.withRequestTimeout(config.ifsTimeout))
      .execute[HttpResponse].map { response =>
      response.status match {
        case OK =>
          logger.info("GetAFT details returned response with status OK")
          Json.parse(response.body)
        case _ =>
          logger.warn(s"GetAFT details returned response with status ${response.status}")
          handleErrorResponse("GET", url.toString)(response)
      }
    } andThen {
      case Failure(t: Throwable) => logger.warn("Unable to get aft details", t)
    }
  }

  def getIsAftNonZero(pstr: String, startDate: String, aftVersion: String, srn: String, loggedInAsPsa: Boolean)
                     (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Boolean] = {
    val url = url"${Uri(config.isAftNonZero.format(srn))
                .withQuery(Uri.Query("loggedInAsPsa" -> s"$loggedInAsPsa"))}"
    val headers: Seq[(String, String)] = Seq("pstr" -> pstr, "startDate" -> startDate, "aftVersion" -> aftVersion)
    val aftHc = hc.withExtraHeaders(headers = headers:_*)
    httpClient2
      .get(url)(aftHc)
      .setHeader(headers :_*)
      .transform(_.withRequestTimeout(config.ifsTimeout))
      .execute[HttpResponse].map { response =>
      response.status match {
        case OK => response.json.as[Boolean]
        case _ => handleErrorResponse("GET", url.toString)(response)
      }
    }
  }

  def getListOfVersions(pstr: String, startDate: String, srn: String, loggedInAsPsa: Boolean)
                       (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[VersionsWithSubmitter]] = {
    val url = url"${Uri(config.aftListOfVersions.format(srn))
      .withQuery(Uri.Query("loggedInAsPsa" -> s"$loggedInAsPsa"))}"
    val schemeHc = hc.withExtraHeaders("pstr" -> pstr, "startDate" -> startDate)
    httpClient2
      .get(url)(schemeHc)
      .transform(_.withRequestTimeout(config.ifsTimeout))
      .execute[HttpResponse].map{response =>
        response.status match {
          case OK =>
            Json.parse(response.body).validate[Seq[VersionsWithSubmitter]] match {
              case JsSuccess(value, _) => value
              case JsError(errors) => throw JsResultException(errors)
            }
          case NOT_FOUND =>
            Seq.empty
          case _ =>
            handleErrorResponse("GET", url.toString)(response)
        }} andThen {
      case Failure(t: Throwable) => logger.warn("Unable to get list of versions", t)
    }

  }

  def getAftOverview(pstr: String, srn: String, loggedInAsPsa: Boolean, startDate: Option[String] = None, endDate: Option[String] = None)
                    (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[AFTOverview]] = {
    val url = url"${Uri(config.aftOverviewUrl.format(srn))
                  .withQuery(Uri.Query("loggedInAsPsa" -> s"$loggedInAsPsa"))}"
    val headers: Seq[(String, String)] = Seq("pstr" -> pstr, "startDate" -> aftOverviewStartDate.toString, "endDate" -> aftOverviewEndDate.toString)
    val schemeHc = hc.withExtraHeaders(headers = headers:_*)
    httpClient2
      .get(url)(schemeHc)
      .setHeader(headers: _*)
      .transform(_.withRequestTimeout(config.ifsTimeout))
      .execute[HttpResponse].map { response =>
      response.status match {
        case OK =>
          Json.parse(response.body).validate[Seq[AFTOverview]] match {
            case JsSuccess(value, _) => filterOverviewResponse(startDate, endDate, value)
            case JsError(errors) => throw JsResultException(errors)
          }
        case _ =>
          handleErrorResponse("GET", url.toString)(response)
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
