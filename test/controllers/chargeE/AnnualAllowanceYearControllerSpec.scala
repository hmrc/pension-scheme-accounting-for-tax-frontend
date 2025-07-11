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

package controllers.chargeE

import connectors.SchemeDetailsConnector
import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData._
import forms.YearRangeFormProvider
import matchers.JsonMatchers
import models.LocalDateBinder._
import models.requests.IdentifierRequest
import models.{Enumerable, NormalMode, UserAnswers, YearRange}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, times, verify, when}
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import org.scalatest.BeforeAndAfterEach
import pages.chargeE.{AnnualAllowanceMembersQuery, AnnualAllowanceYearPage}
import play.api.Application
import play.api.inject.bind
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers._
import utils.DateHelper
import views.html.chargeE.AnnualAllowanceYearView

import java.time.LocalDate
import scala.concurrent.Future

class AnnualAllowanceYearControllerSpec extends ControllerSpecBase with JsonMatchers with BeforeAndAfterEach with Enumerable.Implicits {
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val mockSchemeDetailsConnector = mock[SchemeDetailsConnector]

  private val application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction)
    .overrides(
      bind[SchemeDetailsConnector].toInstance(mockSchemeDetailsConnector)
    )
    .build()

  private val valuesValid: Map[String, Seq[String]] = Map(
    "value" -> Seq("2019")
  )
  private val valuesValid2: Map[String, Seq[String]] = Map(
    "value" -> Seq("2011")
  )
  private val valuesInvalid: Map[String, Seq[String]] = Map(
    "value" -> Seq("Unknown Year")
  )
  val submitUrl = controllers.chargeE.routes.AnnualAllowanceYearController.onSubmit(NormalMode, srn, startDate, accessType, versionInt, 0)
  val returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url

  private def form = new YearRangeFormProvider()()

  private def httpPathGET: String = controllers.chargeE.routes.AnnualAllowanceYearController.onPageLoad(NormalMode,
    srn, startDate, accessType, versionInt, 0).url

  private def httpPathPOST: String = controllers.chargeE.routes.AnnualAllowanceYearController.onSubmit(NormalMode,
    srn, startDate, accessType, versionInt, 0).url

  override def beforeEach(): Unit = {
    super.beforeEach()
    when(mockUserAnswersCacheConnector.savePartial(any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
    when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[?])).thenReturn(dummyCall.url)
  }

  DateHelper.setDate(Some(LocalDate.of(2020, 4, 5)))

  private val userAnswers: Option[UserAnswers] = Some(userAnswersWithSchemeNamePstrQuarter)

  "AnnualAllowanceYear Controller" must {

    "return OK and the correct view for a GET" in {
      mutableFakeDataRetrievalAction.setDataToReturn(Option(userAnswersWithSchemeNamePstrQuarter))
      when(mockSchemeDetailsConnector.getSchemeDetails(any(), any())(any(), any())).thenReturn(Future.successful(schemeDetails))
      val request = httpGETRequest(httpPathGET)

      val view = application.injector.instanceOf[AnnualAllowanceYearView].apply(
        form,
        schemeName,
        submitUrl,
        returnUrl,
        YearRange.radios(form)
      )(request, messages)

      val result = route(application, request).value

      status(result) mustEqual OK

      compareResultAndView(result, view)
    }

    "return OK and the correct view for a GET when the question has previously been answered" in {
      reset(mockSchemeDetailsConnector)
      val ua = userAnswersWithSchemeNamePstrQuarter.set(AnnualAllowanceYearPage(0), YearRange("2019"))(
        writes(using YearRange.enumerable)
      ).get
      mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))
      when(mockSchemeDetailsConnector.getSchemeDetails(any(), any())(any(), any())).thenReturn(Future.successful(schemeDetails))
      val request = httpGETRequest(httpPathGET)

      val view = application.injector.instanceOf[AnnualAllowanceYearView].apply(
        form.fill(YearRange("2019")),
        schemeName,
        submitUrl,
        returnUrl,
        YearRange.radios(form)
      )(request, messages)

      val result = route(application, request).value

      status(result) mustEqual OK

      compareResultAndView(result, view)
    }


    "Save data to user answers and redirect to next page when valid data is submitted" in {

      val expectedJson = Json.obj(
        "chargeEDetails" -> Json.obj(
          AnnualAllowanceMembersQuery.toString -> Json.arr(
            Json.obj(
              AnnualAllowanceYearPage.toString -> Json.toJson("2011")
            )
          )
        )
      )
      when(mockCompoundNavigator.nextPage(ArgumentMatchers.eq(AnnualAllowanceYearPage(0)), any(), any(), any(), any(), any(), any())(any())).
        thenReturn(dummyCall)

      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)

      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid2)).value

      status(result) mustEqual SEE_OTHER

      verify(mockUserAnswersCacheConnector, times(1)).savePartial(any(), jsonCaptor.capture, any(), any())(any(), any())

      jsonCaptor.getValue must containJson(expectedJson)

      redirectLocation(result) mustBe Some(dummyCall.url)
    }

    "return a BAD REQUEST when invalid data is submitted" in {
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesInvalid)).value

      status(result) mustEqual BAD_REQUEST

      verify(mockUserAnswersCacheConnector, times(0)).save(any(), any())(any(), any())
    }

    "redirect to Session Expired page for a POST when there is no data" in {
      mutableFakeDataRetrievalAction.setDataToReturn(None)

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad.url
    }
  }
}
