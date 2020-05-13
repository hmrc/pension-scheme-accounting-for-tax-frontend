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

package controllers.amend

import java.time.LocalDate

import config.FrontendAppConfig
import connectors.AFTConnector
import connectors.cache.UserAnswersCacheConnector
import controllers.DataRetrievals
import controllers.actions._
import forms.ConfirmSubmitAFTReturnFormProvider
import helpers.AmendmentHelper
import javax.inject.Inject
import models.LocalDateBinder._
import models.requests.DataRequest
import models.{GenericViewModel, NormalMode, UserAnswers}
import navigators.CompoundNavigator
import pages.ConfirmSubmitAFTAmendmentPage
import play.api.data.Form
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc._
import renderer.Renderer
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.{NunjucksSupport, Radios}

import scala.concurrent.{ExecutionContext, Future}

class ConfirmSubmitAFTAmendmentController @Inject()(override val messagesApi: MessagesApi,
                                                    userAnswersCacheConnector: UserAnswersCacheConnector,
                                                    navigator: CompoundNavigator,
                                                    identify: IdentifierAction,
                                                    getData: DataRetrievalAction,
                                                    allowAccess: AllowAccessActionProvider,
                                                    allowSubmission: AllowSubmissionAction,
                                                    requireData: DataRequiredAction,
                                                    formProvider: ConfirmSubmitAFTReturnFormProvider,
                                                    val controllerComponents: MessagesControllerComponents,
                                                    config: FrontendAppConfig,
                                                    aftConnector: AFTConnector,
                                                    amendmentHelper: AmendmentHelper,
                                                    renderer: Renderer)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  private val form = formProvider()

  def onPageLoad(srn: String, startDate: LocalDate): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen
      requireData andThen allowAccess(srn, startDate)).async { implicit request =>
      val preparedForm = request.userAnswers.get(ConfirmSubmitAFTAmendmentPage) match {
        case None        => form
        case Some(value) => form.fill(value)
      }

      populateView(srn, startDate, request.userAnswers, preparedForm, Results.Status(OK))
    }

  def onSubmit(srn: String, startDate: LocalDate): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen
      requireData andThen allowAccess(srn, startDate)).async { implicit request =>
      form
        .bindFromRequest()
        .fold(
          formWithErrors => {
            populateView(srn, startDate, request.userAnswers, formWithErrors, Results.Status(BAD_REQUEST))
          },
          value =>
            if (!value) {
              userAnswersCacheConnector.removeAll(request.internalId).map { _ => Redirect(config.managePensionsSchemeSummaryUrl.format(srn))
              }
            } else {
              for {
                updatedAnswers <- Future.fromTry(request.userAnswers.set(ConfirmSubmitAFTAmendmentPage, value))
                _ <- userAnswersCacheConnector.save(request.internalId, updatedAnswers.data)
              } yield Redirect(navigator.nextPage(ConfirmSubmitAFTAmendmentPage, NormalMode, updatedAnswers, srn, startDate))
          }
        )
    }

  private def populateView(srn: String, startDate: LocalDate, ua: UserAnswers, form: Form[Boolean], result: Results.Status)(
      implicit request: DataRequest[AnyContent]): Future[Result] = {

    DataRetrievals.retrieveSchemeWithPSTRAndVersion { (schemeName, pstr, amendedVersion) =>
      val previousVersion = amendedVersion - 1
      aftConnector.getAFTDetails(pstr, startDate, aftVersion = s"$previousVersion").flatMap { previousVersionJsValue =>
        val (currentTotalAmountUK, currentTotalAmountNonUK) = amendmentHelper.getTotalAmount(ua)
        val (previousTotalAmountUK, previousTotalAmountNonUK) = amendmentHelper.getTotalAmount(UserAnswers(previousVersionJsValue.as[JsObject]))

        val viewModel = GenericViewModel(
          submitUrl = routes.ConfirmSubmitAFTAmendmentController.onSubmit(srn, startDate).url,
          returnUrl = config.managePensionsSchemeSummaryUrl.format(srn),
          schemeName = schemeName
        )

        val json = Json.obj(
          fields = "srn" -> srn,
          "startDate" -> Some(startDate),
          "form" -> form,
          "versionNumber" -> amendedVersion,
          "viewModel" -> viewModel,
          "tableRowsUK" -> amendmentHelper.amendmentSummaryRows(currentTotalAmountUK, previousTotalAmountUK, amendedVersion, previousVersion),
          "tableRowsNonUK" -> amendmentHelper.amendmentSummaryRows(currentTotalAmountNonUK,
                                                                   previousTotalAmountNonUK,
                                                                   amendedVersion,
                                                                   previousVersion),
          "radios" -> Radios.yesNo(form("value"))
        )

        renderer.render(template = "confirmSubmitAFTAmendment.njk", json).map(result(_))
      }
    }
  }
}