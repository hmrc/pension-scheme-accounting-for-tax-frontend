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

import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData._
import forms.MemberDetailsFormProvider
import matchers.JsonMatchers
import models.LocalDateBinder._
import models.requests.IdentifierRequest
import models.{MemberDetails, NormalMode, UserAnswers}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import pages.chargeE.MemberDetailsPage
import play.api.Application
import play.api.data.Form
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers._
import views.html.MemberDetailsView

import scala.concurrent.Future

class MemberDetailsControllerSpec extends ControllerSpecBase with JsonMatchers {
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private def application: Application = registerApp(applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction).build())
  val formProvider = new MemberDetailsFormProvider()
  val form: Form[MemberDetails] = formProvider()

  lazy val httpPathGET: String =
    controllers.chargeE.routes.MemberDetailsController.onPageLoad(NormalMode, srn, startDate, accessType, versionInt, 0).url
  lazy val httpPathPOST: String =
    controllers.chargeE.routes.MemberDetailsController.onSubmit(NormalMode, srn, startDate, accessType, versionInt, 0).url

  private val valuesValid: Map[String, Seq[String]] = Map(
    "firstName" -> Seq("first"),
    "lastName" -> Seq("last"),
    "nino" -> Seq("AB123456C")
  )

  private val expectedJson: JsObject = Json.obj(
    "pstr" -> "pstr",
    "chargeEDetails" -> Json.obj(
      "members" -> Json.arr(
        Json.obj(
          "memberDetails" -> Json.obj(
            "firstName" -> "first",
            "lastName" -> "last",
            "nino" -> "AB123456C"
          ),
          "memberFormCompleted" -> false
        )
      )),
    "schemeName" -> "Big Scheme"
  )

  private val valuesInvalid: Map[String, Seq[String]] = Map(
    "firstName" -> Seq("***"),
    "lastName" -> Seq("***"),
    "nino" -> Seq("***")
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    when(mockUserAnswersCacheConnector.savePartial(any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
    when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[?])).thenReturn(dummyCall.url)
  }

  private val userAnswers: Option[UserAnswers] = Some(userAnswersWithSchemeNamePstrQuarter)

  "MemberDetails Controller" must {
    "return OK and the correct view for a GET" in {
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      val view = application.injector.instanceOf[MemberDetailsView].apply(
        "chargeE",
        form,
        controllers.chargeE.routes.MemberDetailsController.onSubmit(NormalMode, srn, startDate, accessType, versionInt, 0),
        controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url,
        schemeName
      )(httpGETRequest(httpPathGET), messages)

      compareResultAndView(result, view)
    }

    "return OK and the correct view for a GET when the question has previously been answered" in {
      val ua = userAnswers.map(_.set(MemberDetailsPage(0), memberDetails)).get.toOption.get

      mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      val view = application.injector.instanceOf[MemberDetailsView].apply(
        "chargeE",
        form.fill(memberDetails),
        controllers.chargeE.routes.MemberDetailsController.onSubmit(NormalMode, srn, startDate, accessType, versionInt, 0),
        controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url,
        schemeName
      )(httpGETRequest(httpPathGET), messages)

      compareResultAndView(result, view)
    }

    "redirect to Session Expired page for a GET when there is no data" in {
      mutableFakeDataRetrievalAction.setDataToReturn(None)

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad.url
    }

    "Save data to user answers and redirect to next page when valid data is submitted" in {

      when(mockCompoundNavigator.nextPage(ArgumentMatchers.eq(MemberDetailsPage(0)), any(), any(), any(), any(), any(), any())(any())).thenReturn(dummyCall)

      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)

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

    "redirect to Session Expired page for a POST when there is no data" in {
      mutableFakeDataRetrievalAction.setDataToReturn(None)

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad.url
    }
  }
}
