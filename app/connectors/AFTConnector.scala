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

package connectors

import java.time.LocalDate

import com.google.inject.Inject
import config.FrontendAppConfig
import models.{AFTOverview, Quarters, UserAnswers}
import play.api.Logger
import play.api.http.Status
import play.api.http.Status.OK
import play.api.libs.json.{JsError, JsObject, JsResultException, JsSuccess, JsValue, Json}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import utils.{DateHelper, HttpResponseHelper}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Failure

class AFTConnector @Inject()(http: HttpClient, config: FrontendAppConfig) extends HttpResponseHelper {

  def fileAFTReturn(pstr: String, answers: UserAnswers)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Unit] = {
    val url = config.aftFileReturn
    val aftHc = hc.withExtraHeaders(headers = "pstr" -> pstr)
    http.POST[JsObject, HttpResponse](url, answers.data)(implicitly, implicitly, aftHc, implicitly).map(_ => ())
  }

  def getAFTDetails(pstr: String, startDate: String, aftVersion: String)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[JsValue] = {
    val url = config.getAftDetails
    val aftHc = hc.withExtraHeaders(headers = "pstr" -> pstr, "startDate" -> startDate, "aftVersion" -> aftVersion)
    http.GET[JsValue](url)(implicitly, aftHc, implicitly)
  }

  def getListOfVersions(pstr: String, startDate: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[Int]] = {
    val url = config.aftListOfVersions
    val schemeHc = hc.withExtraHeaders("pstr" -> pstr, "startDate" -> startDate)
    http.GET[HttpResponse](url)(implicitly, schemeHc, implicitly).map { response =>
      require(response.status == Status.OK)
      response.json.as[Seq[Int]]
    }
  }

  def getAftOverview(pstr: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[AFTOverview]] = {
    val url = config.aftOverviewUrl

    val schemeHc = hc.withExtraHeaders("pstr" -> pstr, "startDate" -> startDate.toString, "endDate" -> endDate.toString)

    http.GET[HttpResponse](url)(implicitly, schemeHc, implicitly).map { response =>
      response.status match {
        case OK =>
          val json = Json.parse(response.body)
          json.validate[Seq[AFTOverview]] match {
            case JsSuccess(value, _) => value
            case JsError(errors) => throw JsResultException(errors)
          }
        case _ => handleErrorResponse("GET", url)(response)
      }
    } andThen {
      case Failure(t: Throwable) => Logger.warn("Unable to get aft overview", t)
    }
  }

  def endDate: LocalDate = Quarters.getQuarter(DateHelper.today).endDate

  def startDate: LocalDate =  {
    val earliestStartDate = LocalDate.parse(config.earliestStartDate)
    val calculatedStartYear = endDate.minusYears(config.aftNoOfYearsDisplayed).getYear
    val calculatedStartDate = LocalDate.of(calculatedStartYear, 1, 1)

    if(calculatedStartDate.isAfter(earliestStartDate)) {
      calculatedStartDate
    } else {
      earliestStartDate
    }
  }
}
