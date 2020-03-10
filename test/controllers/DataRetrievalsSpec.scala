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

package controllers

import models.UserAnswers
import models.requests.DataRequest
import org.scalatest.FreeSpec
import org.scalatest.MustMatchers
import org.scalatest.OptionValues
import pages.SchemeNameQuery
import play.api.mvc.AnyContent
import play.api.mvc.Result
import play.api.mvc.Results.Ok
import play.api.test.FakeRequest
import play.api.test.Helpers.OK
import play.api.test.Helpers.status
import play.api.test.Helpers._
import uk.gov.hmrc.domain.PsaId

import scala.concurrent.Future

class DataRetrievalsSpec extends FreeSpec with MustMatchers with OptionValues {

  private val result: String => Future[Result] = { _ =>
    Future.successful(Ok("success result"))
  }

  "retrieveSchemeName must" - {
    "return successful result when scheme name is successfully retrieved from user answers" in {
      val ua = UserAnswers().set(SchemeNameQuery, value = "schemeName").getOrElse(UserAnswers())
      val request: DataRequest[AnyContent] = DataRequest(FakeRequest(GET, "/"), "test-internal-id", PsaId("A2100000"), ua)
      val res = DataRetrievals.retrieveSchemeName(result)(request)
      status(res) must be(OK)
    }

    "return session expired when there is no scheme name in user answers" in {
      val request: DataRequest[AnyContent] = DataRequest(FakeRequest(GET, "/"), "test-internal-id", PsaId("A2100000"), UserAnswers())
      val res = DataRetrievals.retrieveSchemeName(result)(request)
      redirectLocation(res).value mustBe controllers.routes.SessionExpiredController.onPageLoad().url
    }
  }
}
