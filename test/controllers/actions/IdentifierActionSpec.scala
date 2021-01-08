/*
 * Copyright 2021 HM Revenue & Customs
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

package controllers.actions

import com.google.inject.Inject
import controllers.base.ControllerSpecBase
import controllers.routes
import data.SampleData._
import org.mockito.Matchers.any
import org.mockito.Mockito
import org.mockito.Mockito._
import play.api.libs.json.Json
import play.api.mvc.Results._
import play.api.mvc.{AnyContent, BodyParsers, Action}
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.Retrieval
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, ExecutionContext}

class IdentifierActionSpec
  extends ControllerSpecBase {

  class Harness(authAction: IdentifierAction) {
    def onPageLoad(): Action[AnyContent] = authAction {
      implicit request =>
        Ok(Json.obj("psaId" -> request.psaId))
    }
  }

  class PspHarness(authAction: IdentifierAction) {
    def onPageLoad(): Action[AnyContent] = authAction {
      implicit request =>
        Ok(Json.obj("psaId" -> request.pspId))
    }
  }

  val authConnector: AuthConnector = mock[AuthConnector]

  val bodyParsers: BodyParsers.Default = app.injector.instanceOf[BodyParsers.Default]

  val authAction = new AuthenticatedIdentifierAction(authConnector, frontendAppConfig, bodyParsers)

  override def beforeEach: Unit = {
    Mockito.reset(authConnector)
    when(mockAppConfig.loginUrl).thenReturn(dummyCall.url)
  }

  "Identifier Action" when {

    "the user has logged in with HMRC-PODS-ORG enrolment" must {

      "have the PSAID" in {

        val controller = new Harness(authAction)

        val enrolments = Enrolments(Set(
          Enrolment("HMRC-PODS-ORG", Seq(
            EnrolmentIdentifier("PSAID", "A0000000")
          ), "Activated", None)
        ))

        when(authConnector.authorise[Enrolments](any(), any())(any(), any()))
          .thenReturn(Future.successful(enrolments))

        val result = controller.onPageLoad()(fakeRequest)
        status(result) mustBe OK
        (contentAsJson(result) \ "psaId").asOpt[String].value mustEqual "A0000000"
      }
    }

    "the user has logged in with HMRC-PODSPP-ORG enrolment" must {

      "have the PSPID" in {

        val controller = new PspHarness(authAction)

        val enrolments = Enrolments(Set(
          Enrolment("HMRC-PODSPP-ORG", Seq(
            EnrolmentIdentifier("PSPID", "20000000")
          ), "Activated", None)
        ))

        when(authConnector.authorise[Enrolments](any(), any())(any(), any()))
          .thenReturn(Future.successful(enrolments))

        val result = controller.onPageLoad()(fakeRequest)
        status(result) mustBe OK
        (contentAsJson(result) \ "psaId").asOpt[String].value mustEqual "20000000"
      }
    }

    "the user hasn't logged in" must {

      "redirect the user to log in " in {

        val authAction = new AuthenticatedIdentifierAction(
          new FakeFailingAuthConnector(new MissingBearerToken), frontendAppConfig, bodyParsers
        )
        val controller = new Harness(authAction)
        val result = controller.onPageLoad()(fakeRequest)

        status(result) mustBe SEE_OTHER

        redirectLocation(result).get must startWith(frontendAppConfig.loginUrl)
      }
    }

    "the user's session has expired" must {

      "redirect the user to log in " in {

        val authAction = new AuthenticatedIdentifierAction(
          new FakeFailingAuthConnector(new BearerTokenExpired), frontendAppConfig, bodyParsers
        )
        val controller = new Harness(authAction)
        val result = controller.onPageLoad()(fakeRequest)

        status(result) mustBe SEE_OTHER

        redirectLocation(result).get must startWith(frontendAppConfig.loginUrl)
      }
    }

    "the user doesn't have sufficient enrolments" must {

      "redirect the user to the unauthorised page" in {

        val authAction = new AuthenticatedIdentifierAction(
          new FakeFailingAuthConnector(new InsufficientEnrolments), frontendAppConfig, bodyParsers
        )
        val controller = new Harness(authAction)
        val result = controller.onPageLoad()(fakeRequest)

        status(result) mustBe SEE_OTHER

        redirectLocation(result) mustBe Some(routes.UnauthorisedController.onPageLoad().url)
      }
    }

    "the user doesn't have sufficient confidence level" must {

      "redirect the user to the unauthorised page" in {

        val authAction = new AuthenticatedIdentifierAction(
          new FakeFailingAuthConnector(new InsufficientConfidenceLevel), frontendAppConfig, bodyParsers
        )
        val controller = new Harness(authAction)
        val result = controller.onPageLoad()(fakeRequest)

        status(result) mustBe SEE_OTHER

        redirectLocation(result) mustBe Some(routes.UnauthorisedController.onPageLoad().url)
      }
    }

    "the user used an unaccepted auth provider" must {

      "redirect the user to the unauthorised page" in {

        val authAction = new AuthenticatedIdentifierAction(
          new FakeFailingAuthConnector(new UnsupportedAuthProvider), frontendAppConfig, bodyParsers
        )
        val controller = new Harness(authAction)
        val result = controller.onPageLoad()(fakeRequest)

        status(result) mustBe SEE_OTHER

        redirectLocation(result) mustBe Some(routes.UnauthorisedController.onPageLoad().url)
      }
    }

    "the user has an unsupported affinity group" must {

      "redirect the user to the unauthorised page" in {

        val authAction = new AuthenticatedIdentifierAction(
          new FakeFailingAuthConnector(new UnsupportedAffinityGroup), frontendAppConfig, bodyParsers
        )
        val controller = new Harness(authAction)
        val result = controller.onPageLoad()(fakeRequest)

        status(result) mustBe SEE_OTHER

        redirectLocation(result) mustBe Some(routes.UnauthorisedController.onPageLoad().url)
      }
    }

    "the user has an unsupported credential role" must {

      "redirect the user to the unauthorised page" in {

        val authAction = new AuthenticatedIdentifierAction(
          new FakeFailingAuthConnector(new UnsupportedCredentialRole), frontendAppConfig, bodyParsers
        )
        val controller = new Harness(authAction)
        val result = controller.onPageLoad()(fakeRequest)

        status(result) mustBe SEE_OTHER

        redirectLocation(result) mustBe Some(routes.UnauthorisedController.onPageLoad().url)
      }
    }
  }
}

class FakeFailingAuthConnector @Inject()(exceptionToReturn: Throwable) extends AuthConnector {
  val serviceUrl: String = ""

  override def authorise[A](predicate: Predicate, retrieval: Retrieval[A])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[A] =
    Future.failed(exceptionToReturn)
}
