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

import config.FrontendAppConfig
import connectors.AFTConnector
import connectors.cache.UserAnswersCacheConnector
import controllers.DataRetrievals
import controllers.actions._
import forms.DeleteMemberFormProvider
import javax.inject.Inject
import models.{GenericViewModel, Index, NormalMode, UserAnswers}
import navigators.CompoundNavigator
import pages.chargeE.{DeleteMemberPage, MemberDetailsPage, TotalChargeAmountPage}
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.ChargeEService.getAnnualAllowanceMembers
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.{NunjucksSupport, Radios}

import scala.concurrent.{ExecutionContext, Future}

class DeleteMemberController @Inject()(override val messagesApi: MessagesApi,
                                       userAnswersCacheConnector: UserAnswersCacheConnector,
                                       navigator: CompoundNavigator,
                                       identify: IdentifierAction,
                                       getData: DataRetrievalAction,
                                       allowAccess: AllowAccessActionProvider,
                                       requireData: DataRequiredAction,
                                       aftConnector: AFTConnector,
                                       formProvider: DeleteMemberFormProvider,
                                       val controllerComponents: MessagesControllerComponents,
                                       config: FrontendAppConfig,
                                       renderer: Renderer
                                      )(implicit ec: ExecutionContext) extends FrontendBaseController with I18nSupport with NunjucksSupport {

  private def form(memberName: String)(implicit messages: Messages): Form[Boolean] =
    formProvider(messages("deleteMember.error.required", memberName))

  def onPageLoad(srn: String, index: Index): Action[AnyContent] = (identify andThen getData andThen allowAccess(srn) andThen requireData).async {
    implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>
        request.userAnswers.get(MemberDetailsPage(index)) match {
          case Some(memberDetails) =>
            val viewModel = GenericViewModel(
              submitUrl = routes.DeleteMemberController.onSubmit(srn, index).url,
              returnUrl = config.managePensionsSchemeSummaryUrl.format(srn),
              schemeName = schemeName)

            val json = Json.obj(
              "form" -> form(memberDetails.fullName),
              "viewModel" -> viewModel,
              "radios" -> Radios.yesNo(form(memberDetails.fullName)(implicitly)("value")),
              "memberName" -> memberDetails.fullName
            )

            renderer.render("chargeE/deleteMember.njk", json).map(Ok(_))
          case _ => Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
        }
      }
  }

  def onSubmit(srn: String, index: Index): Action[AnyContent] = (identify andThen getData andThen requireData).async {
    implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>
        request.userAnswers.get(MemberDetailsPage(index)) match {
          case Some(memberDetails) =>
            form(memberDetails.fullName).bindFromRequest().fold(
              formWithErrors => {

                val viewModel = GenericViewModel(
                  submitUrl = routes.DeleteMemberController.onSubmit(srn, index).url,
                  returnUrl = config.managePensionsSchemeSummaryUrl.format(srn),
                  schemeName = schemeName)

                val json = Json.obj(
                  "form" -> formWithErrors,
                  "viewModel" -> viewModel,
                  "radios" -> Radios.yesNo(formWithErrors("value")),
                  "memberName" -> memberDetails.fullName
                )

                renderer.render("chargeE/deleteMember.njk", json).map(BadRequest(_))

              },
              value =>
                if(value) {
                  DataRetrievals.retrievePSTR { pstr =>

                    for {
                      interimAnswers <- Future.fromTry(request.userAnswers.set(MemberDetailsPage(index), memberDetails.copy(isDeleted = true)))
                      updatedAnswers <- Future.fromTry(interimAnswers.set(TotalChargeAmountPage, totalAmount(interimAnswers, srn)))
                      _ <- userAnswersCacheConnector.save(request.internalId, updatedAnswers.data)
                      _ <- aftConnector.fileAFTReturn(pstr, updatedAnswers)
                    } yield Redirect(navigator.nextPage(DeleteMemberPage, NormalMode, updatedAnswers, srn))
                  }
                } else {
                  Future.successful(Redirect(navigator.nextPage(DeleteMemberPage, NormalMode, request.userAnswers, srn)))
                }
            )
          case _ => Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
        }
      }
  }

  def totalAmount(ua: UserAnswers, srn: String): BigDecimal = getAnnualAllowanceMembers(ua, srn).map(_.amount).sum
}
