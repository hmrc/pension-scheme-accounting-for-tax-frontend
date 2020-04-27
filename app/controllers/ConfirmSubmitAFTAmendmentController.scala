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

import java.time.LocalDate

import config.FrontendAppConfig
import connectors.AFTConnector
import connectors.cache.UserAnswersCacheConnector
import controllers.actions._
import forms.ConfirmSubmitAFTReturnFormProvider
import helpers.{AmendmentHelper, FormatHelper}
import javax.inject.Inject
import models.LocalDateBinder._
import models.{GenericViewModel, Mode, UserAnswers}
import navigators.CompoundNavigator
import pages.ConfirmSubmitAFTReturnPage
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.SummaryList.{Key, Row, Value}
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels.{NunjucksSupport, Radios, SummaryList}

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

  def onPageLoad(mode: Mode, srn: String, startDate: LocalDate): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen
      allowAccess(srn, startDate) andThen allowSubmission andThen requireData).async { implicit request =>

      DataRetrievals.retrieveSchemeWithPSTRAndVersion { (schemeName, pstr, amendedVersion) =>
        val previousVersion = amendedVersion - 1
        aftConnector.getAFTDetails(pstr, startDate, aftVersion = s"$previousVersion").flatMap { previousVersionJsValue =>
         val (currentTotalAmountUK, currentTotalAmountNonUK) = amendmentHelper.getTotalAmount(request.userAnswers)
         val (previousTotalAmountUK, previousTotalAmountNonUK) = amendmentHelper.getTotalAmount(UserAnswers(previousVersionJsValue.as[JsObject]))

          val preparedForm = request.userAnswers.get(ConfirmSubmitAFTReturnPage) match {
            case None        => form
            case Some(value) => form.fill(value)
          }

          val viewModel = GenericViewModel(
            submitUrl = routes.ConfirmSubmitAFTReturnController.onSubmit(mode, srn, startDate).url,
            returnUrl = config.managePensionsSchemeSummaryUrl.format(srn),
            schemeName = schemeName
          )

          val json = Json.obj(
            fields = "srn" -> srn,
            "startDate" -> Some(startDate),
            "form" -> preparedForm,
            "viewModel" -> viewModel,
            "listUK" -> getRows(currentTotalAmountUK, previousTotalAmountUK, amendedVersion, previousVersion),
            "listNonUK" -> getRows(currentTotalAmountNonUK, previousTotalAmountNonUK, amendedVersion, previousVersion),
            "radios" -> Radios.yesNo(preparedForm("value"))
          )

          renderer.render(template = "confirmSubmitAFTAmendment.njk", json).map(Ok(_))
        }
      }
    }

  def getRows(currentTotalAmountUK: BigDecimal, previousTotalAmountNonUK: BigDecimal,
              currentVersion: Int, previousVersion: Int)(implicit messages: Messages): Seq[Row] = {
    val differenceAmount = currentTotalAmountUK - previousTotalAmountNonUK
    Seq(Row(
      key = Key(msg"confirmSubmitAFTReturn.total.for".withArgs(previousVersion),
        classes = Seq("govuk-table__header--numeric","govuk-!-padding-right-0")),
      value = Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(currentTotalAmountUK)}"),
        classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric")),
      actions = Nil
    ),
      Row(
        key = Key(msg"confirmSubmitAFTReturn.total.for".withArgs(currentVersion),
          classes = Seq("govuk-table__header--numeric","govuk-!-padding-right-0")),
        value = Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(previousTotalAmountNonUK)}"),
          classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric")),
        actions = Nil
      ),
      Row(
        key = Key(msg"confirmSubmitAFTReturn.difference.between".withArgs(currentVersion, previousVersion),
          classes = Seq("govuk-table__header--numeric","govuk-!-padding-right-0")),
        value = Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(differenceAmount)}"),
          classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric")),
        actions = Nil
      )
    )
  }

  def onSubmit(mode: Mode, srn: String, startDate: LocalDate): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen
      allowAccess(srn, startDate) andThen allowSubmission andThen requireData).async { implicit request =>
      DataRetrievals.retrieveSchemeWithPSTRAndVersion { (schemeName, pstr, amendedVersion) =>
        form
          .bindFromRequest()
          .fold(
            formWithErrors => {

              val viewModel = GenericViewModel(
                submitUrl = routes.ConfirmSubmitAFTReturnController.onSubmit(mode, srn, startDate).url,
                returnUrl = config.managePensionsSchemeSummaryUrl.format(srn),
                schemeName = schemeName
              )

              val json = Json.obj(
                fields = "srn" -> srn,
                "startDate" -> Some(startDate),
                "form" -> formWithErrors,
                "viewModel" -> viewModel,
                "radios" -> Radios.yesNo(formWithErrors("value"))
              )

              renderer.render(template = "confirmSubmitAFTAmendment.njk", json).map(BadRequest(_))
            },
            value =>
              if (!value) {
                userAnswersCacheConnector.removeAll(request.internalId).map { _ => Redirect(config.managePensionsSchemeSummaryUrl.format(srn))
                }
              } else {
                for {
                  updatedAnswers <- Future.fromTry(request.userAnswers.set(ConfirmSubmitAFTReturnPage, value))
                  _ <- userAnswersCacheConnector.save(request.internalId, updatedAnswers.data)
                } yield Redirect(navigator.nextPage(ConfirmSubmitAFTReturnPage, mode, updatedAnswers, srn, startDate))
            }
          )
      }
    }
}
