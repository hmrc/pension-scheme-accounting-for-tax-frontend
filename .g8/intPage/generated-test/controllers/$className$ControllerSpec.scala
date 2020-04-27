package controllers

import config.FrontendAppConfig
import controllers.base.ControllerSpecBase
import matchers.JsonMatchers
import forms.$className$FormProvider
import models.{GenericViewModel, NormalMode, UserAnswers}
import org.mockito.ArgumentCaptor
import org.mockito.Matchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.{OptionValues, TryValues}
import org.scalatestplus.mockito.MockitoSugar
import pages.$className$Page
import play.api.inject.bind
import play.api.libs.json.{JsNumber, JsObject, Json}
import play.api.mvc.Call
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.twirl.api.Html
import data.SampleData._
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.Future

class $className$ControllerSpec extends ControllerSpecBase with MockitoSugar with NunjucksSupport with JsonMatchers with OptionValues with TryValues {

  val mockAppConfig: FrontendAppConfig = mock[FrontendAppConfig]
  val formProvider = new $className$FormProvider()
  val form = formProvider()

  def onwardRoute = Call("GET", "/foo")

  val validAnswer = $minimum$

  def $className;format="decap"$Route = routes.$className$Controller.onPageLoad(NormalMode, srn).url
  def $className;format="decap"$SubmitRoute = routes.$className$Controller.onSubmit(NormalMode, srn).url

  val viewModel = GenericViewModel(
    submitUrl = $className;format="decap"$SubmitRoute,
  returnUrl = onwardRoute.url,
  schemeName = schemeName)

  val answers: UserAnswers = userAnswersWithSchemeName.set($className$Page, validAnswer).success.value

  "$className$ Controller" must {

    "return OK and the correct view for a GET" in {
      when(mockAppConfig.managePensionsSchemeSummaryUrl).thenReturn(onwardRoute.url)
      when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))

      val application = applicationBuilder(userAnswers = Some(userAnswersWithSchemeName))
        .overrides(
          bind[FrontendAppConfig].toInstance(mockAppConfig)
        )
        .build()
      val request = FakeRequest(GET, $className;format="decap"$Route)
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, request).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      val expectedJson = Json.obj(
        "form" -> form,
        "viewModel" -> viewModel
      )

      templateCaptor.getValue mustEqual "$className;format="decap"$.njk"
      jsonCaptor.getValue must containJson(expectedJson)

      application.stop()
    }

    "populate the view correctly on a GET when the question has previously been answered" in {
      when(mockAppConfig.managePensionsSchemeSummaryUrl).thenReturn(onwardRoute.url)
      when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))

      val application = applicationBuilder(userAnswers = Some(answers))
        .overrides(
          bind[FrontendAppConfig].toInstance(mockAppConfig)
        )
        .build()
      val request = FakeRequest(GET, $className;format="decap"$Route)
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, request).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      val filledForm = form.bind(Map("value" -> validAnswer.toString))

      val expectedJson = Json.obj(
        "form" -> filledForm,
        "viewModel" -> viewModel
      )

      templateCaptor.getValue mustEqual "$className;format="decap"$.njk"
      jsonCaptor.getValue must containJson(expectedJson)

      application.stop()
    }

    "redirect to the next page when valid data is submitted" in {

      when(mockAppConfig.managePensionsSchemeSummaryUrl).thenReturn(onwardRoute.url)
      when(mockUserAnswersCacheConnector.save(any(), any(), any(), any())(any(), any())) thenReturn Future.successful(Json.obj())
      when(mockCompoundNavigator.nextPage(any(), any(), any(), any())).thenReturn(onwardRoute)

      val application = applicationBuilder(userAnswers = Some(userAnswersWithSchemeName))
        .overrides(
          bind[FrontendAppConfig].toInstance(mockAppConfig)
        )
        .build()

      val request =
        FakeRequest(POST, $className;format="decap"$Route)
      .withFormUrlEncodedBody(("value", validAnswer.toString))

      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual onwardRoute.url

      application.stop()
    }

    "return a Bad Request and errors when invalid data is submitted" in {
      when(mockAppConfig.managePensionsSchemeSummaryUrl).thenReturn(onwardRoute.url)
      when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))

      val application = applicationBuilder(userAnswers = Some(userAnswersWithSchemeName))
        .overrides(
          bind[FrontendAppConfig].toInstance(mockAppConfig)
        )
        .build()
      val request = FakeRequest(POST, $className;format="decap"$Route).withFormUrlEncodedBody(("value", "invalid value"))
      val boundForm = form.bind(Map("value" -> "invalid value"))
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, request).value

      status(result) mustEqual BAD_REQUEST

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      val expectedJson = Json.obj(
        "form" -> boundForm,
        "viewModel" -> viewModel
      )

      templateCaptor.getValue mustEqual "$className;format="decap"$.njk"
      jsonCaptor.getValue must containJson(expectedJson)

      application.stop()
    }

    "redirect to Session Expired for a GET if no existing data is found" in {

      val application = applicationBuilder(userAnswers = None).build()

      val request = FakeRequest(GET, $className;format="decap"$Route)

      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustEqual routes.SessionExpiredController.onPageLoad().url

      application.stop()
    }

    "redirect to Session Expired for a POST if no existing data is found" in {

      val application = applicationBuilder(userAnswers = None).build()

      val request =
        FakeRequest(POST, $className;format="decap"$Route)
      .withFormUrlEncodedBody(("value", validAnswer.toString))

      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual routes.SessionExpiredController.onPageLoad().url

      application.stop()
    }
  }
}
