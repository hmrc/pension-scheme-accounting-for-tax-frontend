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

package controllers.chargeG

import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData._
import forms.chargeG.MemberDetailsFormProvider
import matchers.JsonMatchers
import models.LocalDateBinder._
import models.chargeG.MemberDetails
import models.requests.IdentifierRequest
import models.{NormalMode, UserAnswers}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import pages.chargeG.MemberDetailsPage
import play.api.Application
import play.api.data.Form
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers._
import views.html.chargeG.MemberDetailsView

import scala.concurrent.Future

class MemberDetailsControllerSpec extends ControllerSpecBase with JsonMatchers {
  private val userAnswers: Option[UserAnswers] = Some(userAnswersWithSchemeNamePstrQuarter)
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private def application: Application = registerApp(applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction).build())
  private val formProvider = new MemberDetailsFormProvider()
  private val form: Form[MemberDetails] = formProvider()

  private lazy val httpPathGET: String =
    controllers.chargeG.routes.MemberDetailsController.onPageLoad(NormalMode, srn, startDate, accessType, versionInt, 0).url
  private lazy val httpPathPOST: String =
    controllers.chargeG.routes.MemberDetailsController.onSubmit(NormalMode, srn, startDate, accessType, versionInt, 0).url

  private val valuesValid: Map[String, Seq[String]] = Map(
    "firstName" -> Seq("first"),
    "lastName" -> Seq("last"),
    "dob.day" -> Seq("3"),
    "dob.month" -> Seq("4"),
    "dob.year" -> Seq("2019"),
    "nino" -> Seq("AB123456C")
  )

  private val expectedJson: JsObject = Json.obj(
    "pstr" -> "pstr",
    "chargeGDetails" -> Json.obj(
      "members" -> Json.arr(
        Json.obj(
          "memberDetails" -> Json.obj(
            "firstName" -> "first",
            "lastName" -> "last",
            "dob" -> "2019-04-03",
            "nino" -> "AB123456C"
          )
        )
      )),
    "schemeName" -> "Big Scheme"
  )

  private val valuesInvalid: Map[String, Seq[String]] = Map(
    "firstName" -> Seq("***"),
    "lastName" -> Seq("***"),
    "dob.day" -> Seq("3"),
    "dob.month" -> Seq("15"),
    "dob.year" -> Seq("2020"),
    "nino" -> Seq("***")
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    when(mockUserAnswersCacheConnector.savePartial(any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
    when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[?])).thenReturn(dummyCall.url)
  }

  "MemberDetails Controller" must {
    "return OK and the correct view for a GET" in {
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)

      val view = application.injector.instanceOf[MemberDetailsView].apply(
        form,
        "chargeG",
        controllers.chargeG.routes.MemberDetailsController.onSubmit(NormalMode, srn, startDate, accessType, versionInt, 0),
        controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url,
        schemeName
      )(httpGETRequest(httpPathGET), messages)

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      compareResultAndView(result, view)
    }

    "return OK and the correct view for a GET when the question has previously been answered" in {
      val ua = userAnswers.map(_.set(MemberDetailsPage(0), memberGDetails)).get.toOption.get

      mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))

      val view = application.injector.instanceOf[MemberDetailsView].apply(
        form.fill(memberGDetails),
        "chargeG",
        controllers.chargeG.routes.MemberDetailsController.onSubmit(NormalMode, srn, startDate, accessType, versionInt, 0),
        controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url,
        schemeName
      )(httpGETRequest(httpPathGET), messages)

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      compareResultAndView(result, view)
    }

    "Save data to user answers and redirect to next page when valid data is submitted" in {
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)

      when(mockCompoundNavigator.nextPage(ArgumentMatchers.eq(MemberDetailsPage(0)), any(), any(), any(), any(), any(), any())(any())).thenReturn(dummyCall)

      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER

      verify(mockUserAnswersCacheConnector, times(1)).savePartial(any(), jsonCaptor.capture, any(), any())(any(), any())

      jsonCaptor.getValue must containJson(expectedJson)

      redirectLocation(result) mustBe Some(dummyCall.url)
    }

    "return a BAD REQUEST when invalid data is submitted" in {
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesInvalid)).value

      status(result) mustEqual BAD_REQUEST

      verify(mockUserAnswersCacheConnector, times(0)).savePartial(any(), any(), any(), any())(any(), any())
    }


    "redirect to Session Expired page for a GET when there is no data" in {
      mutableFakeDataRetrievalAction.setDataToReturn(None)

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad.url
    }

    "redirect to Session Expired page for a POST when there is no data" in {
      mutableFakeDataRetrievalAction.setDataToReturn(None)

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad.url
    }
  }
}
