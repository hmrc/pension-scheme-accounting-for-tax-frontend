package controllers.chargeG

import java.time.{LocalDate, ZoneOffset}

import config.FrontendAppConfig
import controllers.base.ControllerSpecBase
import data.SampleData._
import forms.chargeG.ChargeDetailsFormProvider
import matchers.JsonMatchers
import models.{GenericViewModel, NormalMode, UserAnswers}
import org.mockito.ArgumentCaptor
import org.mockito.Matchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatestplus.mockito.MockitoSugar
import play.api.data.Form
import play.api.inject.bind
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{AnyContentAsEmpty, AnyContentAsFormUrlEncoded, Call}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.twirl.api.Html
import uk.gov.hmrc.viewmodels.{DateInput, NunjucksSupport}

import scala.concurrent.Future

class ChargeDetailsControllerSpec extends ControllerSpecBase with MockitoSugar with NunjucksSupport with JsonMatchers {

  val mockAppConfig: FrontendAppConfig = mock[FrontendAppConfig]
  val formProvider = new ChargeDetailsFormProvider()
  private def form: Form[LocalDate] = formProvider()

  def onwardRoute: Call = Call("GET", "/foo")

  val validAnswer = LocalDate.now(ZoneOffset.UTC)

  def chargeDetailsRoute = routes.ChargeDetailsController.onPageLoad(NormalMode, srn).url
  def chargeDetailsSubmitRoute = routes.ChargeDetailsController.onSubmit(NormalMode, srn).url

  val emptyUserAnswers = UserAnswers(Json.obj())

  def getRequest: FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest(GET, chargeDetailsRoute)

  def postRequest: FakeRequest[AnyContentAsFormUrlEncoded] =
    FakeRequest(POST, chargeDetailsRoute)
  .withFormUrlEncodedBody(
    "value.day"   -> validAnswer.getDayOfMonth.toString,
    "value.month" -> validAnswer.getMonthValue.toString,
    "value.year"  -> validAnswer.getYear.toString
  )

  val viewModel = GenericViewModel(
    submitUrl = chargeDetailsSubmitRoute,
  returnUrl = onwardRoute.url,
  schemeName = schemeName)

  "ChargeDetails Controller" must {

    "return OK and the correct view for a GET" in {

      when(mockAppConfig.managePensionsSchemeSummaryUrl).thenReturn(onwardRoute.url)
      when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))

      val application = applicationBuilder(userAnswers = Some(userAnswersWithSchemeName))
        .overrides(
          bind[FrontendAppConfig].toInstance(mockAppConfig)
        )
        .build()
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, getRequest).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      val date = DateInput.localDate(form("value"))

      val expectedJson = Json.obj(
        "form" -> form,
        "viewModel" -> viewModel,
        "date" -> date
      )

      templateCaptor.getValue mustEqual "chargeDetails.njk"
      jsonCaptor.getValue must containJson(expectedJson)

      application.stop()
    }

    "populate the view correctly on a GET when the question has previously been answered" in {

      when(mockAppConfig.managePensionsSchemeSummaryUrl).thenReturn(onwardRoute.url)
      when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))

      val application = applicationBuilder(userAnswers = Some(userAnswersWithSchemeName))
        .overrides(
          bind[FrontendAppConfig].toInstance(mockAppConfig)
        )
        .build()

      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, getRequest).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      val filledForm = form.bind(
        Map(
          "value.day"   -> validAnswer.getDayOfMonth.toString,
          "value.month" -> validAnswer.getMonthValue.toString,
          "value.year"  -> validAnswer.getYear.toString
        )
      )

      val date = DateInput.localDate(form("value"))

      val expectedJson = Json.obj(
        "form" -> form,
        "viewModel" -> viewModel,
        "date" -> date
      )

      templateCaptor.getValue mustEqual "chargeDetails.njk"
      jsonCaptor.getValue must containJson(expectedJson)

      application.stop()
    }

    "redirect to the next page when valid data is submitted" in {

      when(mockAppConfig.managePensionsSchemeSummaryUrl).thenReturn(onwardRoute.url)
      when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())) thenReturn Future.successful(Json.obj())
      when(mockCompoundNavigator.nextPage(any(), any(), any(), any())).thenReturn(onwardRoute)

      val application = applicationBuilder(userAnswers = Some(userAnswersWithSchemeName))
        .overrides(
          bind[FrontendAppConfig].toInstance(mockAppConfig)
        )
        .build()

      val result = route(application, postRequest).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual onwardRoute.url

      application.stop()
    }

    "return a Bad Request and errors when invalid data is submitted" in {

      when(mockRenderer.render(any(), any())(any()))
        .thenReturn(Future.successful(Html("")))
      when(mockAppConfig.managePensionsSchemeSummaryUrl).thenReturn(onwardRoute.url)


      val application = applicationBuilder(userAnswers = Some(userAnswersWithSchemeName))
        .overrides(
          bind[FrontendAppConfig].toInstance(mockAppConfig)
        )
        .build()

      val request = FakeRequest(POST, chargeDetailsRoute).withFormUrlEncodedBody(("value", "invalid value"))
      val boundForm = form.bind(Map("value" -> "invalid value"))
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, request).value

      status(result) mustEqual BAD_REQUEST

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      val date = DateInput.localDate(boundForm("value"))

      val expectedJson = Json.obj(
        "form" -> boundForm,
        "viewModel" -> viewModel,
        "date" -> date
      )

      templateCaptor.getValue mustEqual "chargeDetails.njk"
      jsonCaptor.getValue must containJson(expectedJson)

      application.stop()
    }

    "redirect to Session Expired for a GET if no existing data is found" in {

      val application = applicationBuilder(userAnswers = None).build()

      val result = route(application, getRequest).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustEqual routes.SessionExpiredController.onPageLoad().url

      application.stop()
    }

    "redirect to Session Expired for a POST if no existing data is found" in {

      val application = applicationBuilder(userAnswers = None).build()

      val result = route(application, postRequest).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual routes.SessionExpiredController.onPageLoad().url

      application.stop()
    }
  }
}
