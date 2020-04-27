package controllers

import config.FrontendAppConfig
import controllers.base.ControllerSpecBase
import forms.$className$FormProvider
import matchers.JsonMatchers
import models.{$className$, GenericViewModel, NormalMode, UserAnswers}
import org.mockito.ArgumentCaptor
import org.mockito.Matchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatestplus.mockito.MockitoSugar
import pages.$className$Page
import play.api.inject.bind
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Call
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.twirl.api.Html
import uk.gov.hmrc.viewmodels.NunjucksSupport
import data.SampleData._
import scala.concurrent.Future

class $className$ControllerSpec extends ControllerSpecBase with MockitoSugar with NunjucksSupport with JsonMatchers {

  val mockAppConfig: FrontendAppConfig = mock[FrontendAppConfig]
  def onwardRoute = Call("GET", "/foo")

  val formProvider = new $className$FormProvider()
  val form = formProvider()

  def $className;format="decap"$Route = routes.$className$Controller.onPageLoad(NormalMode, srn).url
  def $className;format="decap"$SubmitRoute = routes.$className$Controller.onSubmit(NormalMode, srn).url

  val userAnswers = UserAnswers(
    Json.obj(
      "schemeName" -> schemeName,
      "pstr" -> pstr,
      $className$Page.toString -> Json.obj(
        "$field1Name$" -> "value 1",
        "$field2Name$" -> "value 2"
      )
    )
  )

  val viewModel = GenericViewModel(
    submitUrl = $className;format="decap"$SubmitRoute,
  returnUrl = onwardRoute.url,
  schemeName = schemeName)

  "$className$ Controller" must {

    "return OK and the correct view for a GET" in {

      when(mockAppConfig.managePensionsSchemeSummaryUrl).thenReturn(onwardRoute.url)
      when(mockRenderer.render(any(), any())(any()))
        .thenReturn(Future.successful(Html("")))

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
      when(mockRenderer.render(any(), any())(any()))
        .thenReturn(Future.successful(Html("")))

      val application = applicationBuilder(userAnswers = Some(userAnswers))
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

      val filledForm = form.bind(
        Map(
          "$field1Name$" -> "value 1",
          "$field2Name$" -> "value 2"
        )
      )

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

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(
          bind[FrontendAppConfig].toInstance(mockAppConfig)
        )
        .build()


      val request =
        FakeRequest(POST, $className;format="decap"$Route)
      .withFormUrlEncodedBody(("$field1Name$", "value 1"), ("$field2Name$", "value 2"))

      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual onwardRoute.url

      application.stop()
    }

    "return a Bad Request and errors when invalid data is submitted" in {

      when(mockRenderer.render(any(), any())(any()))
        .thenReturn(Future.successful(Html("")))
      when(mockAppConfig.managePensionsSchemeSummaryUrl).thenReturn(onwardRoute.url)


      val application = applicationBuilder(userAnswers = Some(userAnswers))
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
        "form"   -> boundForm,
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
      .withFormUrlEncodedBody(("$field1Name$", "value 1"), ("$field2Name$", "value 2"))

      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual routes.SessionExpiredController.onPageLoad().url

      application.stop()
    }
  }
}
