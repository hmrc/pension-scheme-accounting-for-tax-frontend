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

package controllers.chargeE

import java.time.LocalDate

import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import controllers.DataRetrievals
import controllers.actions._
import forms.chargeE.ChargeDetailsFormProvider
import javax.inject.Inject
import models.LocalDateBinder._
import models.chargeE.ChargeEDetails
import models.{GenericViewModel, Index, Mode, Quarter, Quarters, UserAnswers}
import navigators.CompoundNavigator
import pages.chargeE.{ChargeDetailsPage, MemberDetailsPage}
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.{DateInput, NunjucksSupport, Radios}
import utils.DateHelper.dateFormatterDMY
import models.LocalDateBinder._

import scala.concurrent.{ExecutionContext, Future}

class ChargeDetailsController @Inject()(override val messagesApi: MessagesApi,
                                        userAnswersCacheConnector: UserAnswersCacheConnector,
                                        navigator: CompoundNavigator,
                                        identify: IdentifierAction,
                                        getData: DataRetrievalAction,
                                        allowAccess: AllowAccessActionProvider,
                                        requireData: DataRequiredAction,
                                        formProvider: ChargeDetailsFormProvider,
                                        val controllerComponents: MessagesControllerComponents,
                                        config: FrontendAppConfig,
                                        renderer: Renderer
                                       )(implicit ec: ExecutionContext) extends FrontendBaseController with I18nSupport with NunjucksSupport {

  private def form(ua: UserAnswers, startDate: LocalDate)(implicit messages: Messages): Form[ChargeEDetails] = {
    val endDate = Quarters.getQuarter(startDate).endDate
    formProvider(
      startDate,
      endDate,
      messages("dateNoticeReceived.error.date", startDate.format(dateFormatterDMY), endDate.format(dateFormatterDMY)),
      UserAnswers.deriveMinimumChargeValueAllowed(ua)
    )
  }

  def onPageLoad(mode: Mode, srn: String, startDate: LocalDate, index: Index): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen allowAccess(srn, startDate) andThen requireData).async {
      implicit request =>
        DataRetrievals.retrieveSchemeAndMember(MemberDetailsPage(index)) { (schemeName, memberName) =>

          val preparedForm: Form[ChargeEDetails] = request.userAnswers.get(ChargeDetailsPage(index)) match {
            case Some(value) => form(request.userAnswers, startDate).fill(value)
            case None => form(request.userAnswers, startDate)
          }

          val viewModel = GenericViewModel(
            submitUrl = routes.ChargeDetailsController.onSubmit(mode, srn, startDate, index).url,
            returnUrl = config.managePensionsSchemeSummaryUrl.format(srn),
            schemeName = schemeName)

          val json = Json.obj(
            "srn" -> srn,
            "startDate" -> Some(startDate),
            "form" -> preparedForm,
            "viewModel" -> viewModel,
            "date" -> DateInput.localDate(preparedForm("dateNoticeReceived")),
            "radios" -> Radios.yesNo(preparedForm("isPaymentMandatory")),
            "memberName" -> memberName
          )

          renderer.render(template = "chargeE/chargeDetails.njk", json).map(Ok(_))
        }
    }

  def onSubmit(mode: Mode, srn: String, startDate: LocalDate, index: Index): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData).async {
      implicit request =>
        DataRetrievals.retrieveSchemeAndMember(MemberDetailsPage(index)) { (schemeName, memberName) =>

          form(request.userAnswers, startDate).bindFromRequest().fold(
            formWithErrors => {
              val viewModel = GenericViewModel(
                submitUrl = routes.ChargeDetailsController.onSubmit(mode, srn, startDate, index).url,
                returnUrl = config.managePensionsSchemeSummaryUrl.format(srn),
                schemeName = schemeName)

              val json = Json.obj(
                "srn" -> srn,
                "startDate" -> Some(startDate),
                "form" -> formWithErrors,
                "viewModel" -> viewModel,
                "date" -> DateInput.localDate(formWithErrors("dateNoticeReceived")),
                "radios" -> Radios.yesNo(formWithErrors("isPaymentMandatory")),
                "memberName" -> memberName
              )
              renderer.render(template = "chargeE/chargeDetails.njk", json).map(BadRequest(_))
            },
            value => {
              for {
                updatedAnswers <- Future.fromTry(request.userAnswers.set(ChargeDetailsPage(index), value))
                _ <- userAnswersCacheConnector.save(request.internalId, updatedAnswers.data)
              } yield Redirect(navigator.nextPage(ChargeDetailsPage(index), mode, updatedAnswers, srn, startDate))
            }
          )
        }
    }
}
