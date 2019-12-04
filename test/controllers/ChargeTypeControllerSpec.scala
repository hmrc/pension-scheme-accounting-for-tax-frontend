/*
 * Copyright 2019 HM Revenue & Customs
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

import base.SpecBase
import config.FrontendAppConfig
import connectors.SchemeDetailsConnector
import connectors.cache.UserAnswersCacheConnector
import forms.ChargeTypeFormProvider
import matchers.JsonMatchers
import models.ChargeType.ChargeTypeAnnualAllowance
import models.{ChargeType, GenericViewModel, NormalMode, UserAnswers}
import navigators.CompoundNavigator
import org.mockito.ArgumentCaptor
import org.mockito.Matchers.any
import org.mockito.Mockito.{reset, times, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import pages.{ChargeTypePage, SchemeNameQuery}
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Call
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.twirl.api.Html
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.Future

class ChargeTypeControllerSpec extends SpecBase with MockitoSugar with NunjucksSupport with JsonMatchers with BeforeAndAfterEach {

  private val srn = "S1000000001"

  private def chargeTypeRoute: String = routes.ChargeTypeController.onPageLoad(NormalMode, srn).url

  private val schemeName = "test-scheme"
  private val formProvider = new ChargeTypeFormProvider()
  private val form = formProvider()
  private val mockSchemeDetailsConnector = mock[SchemeDetailsConnector]
  private val mockUserAnswersCacheConnector = mock[UserAnswersCacheConnector]
  private val mockNavigator = mock[CompoundNavigator]

  private def viewModel(config: FrontendAppConfig) = GenericViewModel(
    submitUrl = routes.ChargeTypeController.onSubmit(NormalMode, srn).url,
    returnUrl = config.managePensionsSchemeSummaryUrl.format(srn),
    schemeName = schemeName
  )

  private def onwardRoute: Call = controllers.routes.SessionExpiredController.onPageLoad()

  override def beforeEach: Unit = {
    reset(mockRenderer, mockSchemeDetailsConnector, mockUserAnswersCacheConnector)
  }

  "ChargeType Controller" when {

    "on a GET" must {
      val request = FakeRequest(GET, chargeTypeRoute)
      "return OK and the correct view" in {
        running(_.overrides(modules(None) ++
          Seq[GuiceableModule](bind[SchemeDetailsConnector].toInstance(mockSchemeDetailsConnector),
            bind[UserAnswersCacheConnector].toInstance(mockUserAnswersCacheConnector)): _*)
        ) { app =>
          when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
          when(mockSchemeDetailsConnector.getSchemeName(any(), any(), any())(any(), any())).thenReturn(Future.successful(schemeName))
          when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))

          val templateCaptor = ArgumentCaptor.forClass(classOf[String])
          val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])
          val controller = app.injector.instanceOf[ChargeTypeController]
          val config = app.injector.instanceOf[FrontendAppConfig]

          val result = controller.onPageLoad(NormalMode, srn)(request)

          status(result) mustEqual OK
          verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())
          verify(mockUserAnswersCacheConnector, times(1)).save(any(), any())(any(), any())

          val expectedJson = Json.obj(
            fields = "form" -> form,
            "radios" -> ChargeType.radios(form),
            "viewModel" -> viewModel(config)
          )
          templateCaptor.getValue mustEqual "chargeType.njk"
          jsonCaptor.getValue must containJson(expectedJson)
        }
      }

      "must populate the view correctly on a GET when the question has previously been answered" in {
        val userAnswers = UserAnswers().set(ChargeTypePage, ChargeTypeAnnualAllowance).success.value
        val preparedForm = form.fill(ChargeTypeAnnualAllowance)
        running(_.overrides(
          modules(Some(userAnswers)) ++
            Seq[GuiceableModule](bind[SchemeDetailsConnector].toInstance(mockSchemeDetailsConnector),
              bind[UserAnswersCacheConnector].toInstance(mockUserAnswersCacheConnector)): _*)
        ) { app =>
          when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
          when(mockSchemeDetailsConnector.getSchemeName(any(), any(), any())(any(), any())).thenReturn(Future.successful(schemeName))
          when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))

          val templateCaptor = ArgumentCaptor.forClass(classOf[String])
          val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])
          val controller = app.injector.instanceOf[ChargeTypeController]
          val config = app.injector.instanceOf[FrontendAppConfig]

          val result = controller.onPageLoad(NormalMode, srn)(request)

          status(result) mustEqual OK

          verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())
          verify(mockUserAnswersCacheConnector, times(1)).save(any(), any())(any(), any())

          val expectedJson = Json.obj(
            fields = "form" -> preparedForm,
            "radios" -> ChargeType.radios(preparedForm),
            "viewModel" -> viewModel(config)
          )

          templateCaptor.getValue mustEqual "chargeType.njk"
          jsonCaptor.getValue must containJson(expectedJson)
        }
      }
    }

    "on a POST" must {

      "must redirect to the next page when valid data is submitted" in {
        running(_.overrides(
          modules(Some(UserAnswers().set(SchemeNameQuery, schemeName).success.value)) ++
            Seq[GuiceableModule](bind[UserAnswersCacheConnector].toInstance(mockUserAnswersCacheConnector),
              bind[CompoundNavigator].toInstance(mockNavigator)): _*)
        ) { app =>
          when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
          when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
          when(mockNavigator.nextPage(any(), any(), any(), any())(any(), any())).thenReturn(onwardRoute)
          val request = FakeRequest(POST, chargeTypeRoute).withFormUrlEncodedBody(("value", ChargeType.values.head.toString))

          val controller = app.injector.instanceOf[ChargeTypeController]

          val result = controller.onSubmit(NormalMode, srn)(request)

          status(result) mustEqual SEE_OTHER

          redirectLocation(result).value mustEqual onwardRoute.url
        }
      }

      "must return a Bad Request and errors when invalid data is submitted" in {
        val boundForm = form.bind(Map("value" -> "invalid value"))
        running(_.overrides(
          modules(Some(UserAnswers().set(SchemeNameQuery, schemeName).success.value)) ++
            Seq[GuiceableModule](bind[UserAnswersCacheConnector].toInstance(mockUserAnswersCacheConnector),
              bind[CompoundNavigator].toInstance(mockNavigator)): _*)
        ) { app =>
          when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
          when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
          when(mockNavigator.nextPage(any(), any(), any(), any())(any(), any())).thenReturn(onwardRoute)

          val request = FakeRequest(POST, chargeTypeRoute).withFormUrlEncodedBody(("value", "invalid value"))
          val templateCaptor = ArgumentCaptor.forClass(classOf[String])
          val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])
          val controller = app.injector.instanceOf[ChargeTypeController]
          val config = app.injector.instanceOf[FrontendAppConfig]

          val result = controller.onSubmit(NormalMode, srn)(request)
          status(result) mustEqual BAD_REQUEST

          verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

          val expectedJson = Json.obj(
            "form" -> boundForm,
            "radios" -> ChargeType.radios(boundForm),
            "viewModel" -> viewModel(config)
          )

          templateCaptor.getValue mustEqual "chargeType.njk"
          jsonCaptor.getValue must containJson(expectedJson)
        }
      }

      "must redirect to Session Expired if no existing data is found" in {

        running(_.overrides(modules(None): _*)) { app =>
          val request = FakeRequest(POST, chargeTypeRoute).withFormUrlEncodedBody(("value", ChargeType.values.head.toString))
          val controller = app.injector.instanceOf[ChargeTypeController]
          val result = controller.onSubmit(NormalMode, srn)(request)

          status(result) mustEqual SEE_OTHER

          redirectLocation(result).value mustEqual routes.SessionExpiredController.onPageLoad().url
        }
      }
    }
  }
}
