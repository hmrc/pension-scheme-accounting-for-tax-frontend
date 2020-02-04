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

package controllers.chargeC

import config.FrontendAppConfig
import connectors.AFTConnector
import connectors.cache.UserAnswersCacheConnector
import controllers.DataRetrievals
import controllers.actions._
import forms.DeleteMemberFormProvider
import javax.inject.Inject
import models.{GenericViewModel, Index, NormalMode, UserAnswers}
import navigators.CompoundNavigator
import pages.chargeC.{DeleteEmployerPage, IsSponsoringEmployerIndividualPage, SponsoringIndividualDetailsPage, SponsoringOrganisationDetailsPage, TotalChargeAmountPage}
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.AFTService
import services.ChargeCService.getSponsoringEmployers
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.{NunjucksSupport, Radios}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Try}

class DeleteEmployerController @Inject()(override val messagesApi: MessagesApi,
                                       userAnswersCacheConnector: UserAnswersCacheConnector,
                                       navigator: CompoundNavigator,
                                       identify: IdentifierAction,
                                       getData: DataRetrievalAction,
                                       allowAccess: AllowAccessActionProvider,
                                       requireData: DataRequiredAction,
                                       aftService: AFTService,
                                       formProvider: DeleteMemberFormProvider,
                                       val controllerComponents: MessagesControllerComponents,
                                       config: FrontendAppConfig,
                                       renderer: Renderer
                                      )(implicit ec: ExecutionContext) extends FrontendBaseController with I18nSupport with NunjucksSupport {

  private def form(memberName: String)(implicit messages: Messages): Form[Boolean] =
    formProvider(messages("deleteEmployer.chargeC.error.required", memberName))

  def onPageLoad(srn: String, index: Index): Action[AnyContent] = (identify andThen getData andThen allowAccess(srn) andThen requireData).async {
    implicit request =>
      DataRetrievals.retrieveSchemeAndSponsoringEmployer(index) { (schemeName, employerName) =>

            val viewModel = GenericViewModel(
              submitUrl = routes.DeleteEmployerController.onSubmit(srn, index).url,
              returnUrl = config.managePensionsSchemeSummaryUrl.format(srn),
              schemeName = schemeName)

            val json = Json.obj(
              "form" -> form(employerName),
              "viewModel" -> viewModel,
              "radios" -> Radios.yesNo(form(employerName)(implicitly)("value")),
              "employerName" -> employerName
            )

            renderer.render("chargeC/deleteEmployer.njk", json).map(Ok(_))
        }
  }

  def onSubmit(srn: String, index: Index): Action[AnyContent] = (identify andThen getData andThen requireData).async {
    implicit request =>
      DataRetrievals.retrieveSchemeAndSponsoringEmployer(index) { (schemeName, employerName) =>

            form(employerName).bindFromRequest().fold(
              formWithErrors => {

                val viewModel = GenericViewModel(
                  submitUrl = routes.DeleteEmployerController.onSubmit(srn, index).url,
                  returnUrl = config.managePensionsSchemeSummaryUrl.format(srn),
                  schemeName = schemeName)

                val json = Json.obj(
                  "form" -> formWithErrors,
                  "viewModel" -> viewModel,
                  "radios" -> Radios.yesNo(formWithErrors("value")),
                  "employerName" -> employerName
                )

                renderer.render("chargeC/deleteEmployer.njk", json).map(BadRequest(_))

              },
              value =>
                if(value) {
                  DataRetrievals.retrievePSTR { pstr =>

                    for {
                      interimAnswers <- Future.fromTry(saveDeletion(request.userAnswers, index))
                      updatedAnswers <- Future.fromTry(interimAnswers.set(TotalChargeAmountPage, totalAmount(interimAnswers, srn)))
                      _ <- userAnswersCacheConnector.save(request.internalId, updatedAnswers.data)
                      _ <- aftService.fileAFTReturn(pstr, updatedAnswers)
                    } yield Redirect(navigator.nextPage(DeleteEmployerPage, NormalMode, updatedAnswers, srn))
                  }
                } else {
                  Future.successful(Redirect(navigator.nextPage(DeleteEmployerPage, NormalMode, request.userAnswers, srn)))
                }
            )
      }
  }
  private def saveDeletion(ua: UserAnswers, index: Int): Try[UserAnswers] =
    ( ua.get(IsSponsoringEmployerIndividualPage(index)),
      ua.get(SponsoringIndividualDetailsPage(index)),
      ua.get(SponsoringOrganisationDetailsPage(index))) match {
      case (Some(true), Some(individualDetails), _) =>
        ua.set(SponsoringIndividualDetailsPage(index), individualDetails.copy(isDeleted = true))
      case (Some(false), _, Some(orgDetails)) =>
        ua.set(SponsoringOrganisationDetailsPage(index), orgDetails.copy(isDeleted = true))
      case _ => Try(ua)
    }

  def totalAmount(ua: UserAnswers, srn: String): BigDecimal = getSponsoringEmployers(ua, srn).map(_.amount).sum
}
