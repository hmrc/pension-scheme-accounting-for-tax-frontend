/*
 * Copyright 2022 HM Revenue & Customs
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
import connectors.cache.UserAnswersCacheConnector
import controllers.DataRetrievals
import controllers.actions._
import forms.YesNoFormProvider
import helpers.ChargeServiceHelper
import helpers.ErrorHelper.recoverFrom5XX
import models.LocalDateBinder._
import models.{AccessType, GenericViewModel, Index, NormalMode, UserAnswers}
import navigators.CompoundNavigator
import pages.chargeE.{DeleteMemberPage, MemberDetailsPage}
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.{DeleteAFTChargeService, UserAnswersService}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.{NunjucksSupport, Radios}

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class DeleteMemberController @Inject()(override val messagesApi: MessagesApi,
                                       userAnswersCacheConnector: UserAnswersCacheConnector,
                                       userAnswersService: UserAnswersService,
                                       navigator: CompoundNavigator,
                                       identify: IdentifierAction,
                                       getData: DataRetrievalAction,
                                       allowAccess: AllowAccessActionProvider,
                                       requireData: DataRequiredAction,
                                       deleteAFTChargeService: DeleteAFTChargeService,
                                       formProvider: YesNoFormProvider,
                                       val controllerComponents: MessagesControllerComponents,
                                       chargeServiceHelper: ChargeServiceHelper,
                                       config: FrontendAppConfig,
                                       renderer: Renderer)(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  private def form(memberName: String)(implicit messages: Messages): Form[Boolean] =
    formProvider(messages("deleteMember.error.required", memberName))

  def onPageLoad(srn: String, startDate: LocalDate, accessType: AccessType, version: Int, index: Index): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate, None, version, accessType)).async { implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>
        request.userAnswers.get(MemberDetailsPage(index)) match {
          case Some(memberDetails) =>
            val viewModel = GenericViewModel(
              submitUrl = routes.DeleteMemberController.onSubmit(srn, startDate, accessType, version, index).url,
              returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
              schemeName = schemeName
            )

            val json = Json.obj(
              "srn" -> srn,
              "startDate" -> Some(localDateToString(startDate)),
              "form" -> form(memberDetails.fullName),
              "viewModel" -> viewModel,
              "radios" -> Radios.yesNo(form(memberDetails.fullName)(implicitly)("value")),
              "memberName" -> memberDetails.fullName
            )

            renderer.render("chargeE/deleteMember.njk", json).map(Ok(_))
          case _ => Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
        }
      }
    }

  def onSubmit(srn: String, startDate: LocalDate, accessType: AccessType, version: Int, index: Index): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData).async { implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>
        request.userAnswers.get(MemberDetailsPage(index)) match {
          case Some(memberDetails) =>
            form(memberDetails.fullName).bindFromRequest().fold(
                formWithErrors => {

                  val viewModel = GenericViewModel(
                    submitUrl = routes.DeleteMemberController.onSubmit(srn, startDate, accessType, version, index).url,
                    returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
                    schemeName = schemeName
                  )
                  val json = Json.obj(
                    "srn" -> srn,
                    "startDate" -> Some(localDateToString(startDate)),
                    "form" -> formWithErrors,
                    "viewModel" -> viewModel,
                    "radios" -> Radios.yesNo(formWithErrors("value")),
                    "memberName" -> memberDetails.fullName
                  )
                  renderer.render("chargeE/deleteMember.njk", json).map(BadRequest(_))

                },
                value =>
                  if (value) {
                    DataRetrievals.retrievePSTR { pstr =>
                      (for {
                          updatedAnswers <- Future.fromTry(userAnswersService
                            .removeMemberBasedCharge(MemberDetailsPage(index), totalAmount))
                          _ <- deleteAFTChargeService.deleteAndFileAFTReturn(pstr, updatedAnswers)
                        } yield {
                          Redirect(navigator.nextPage(DeleteMemberPage, NormalMode, updatedAnswers, srn, startDate, accessType, version))
                        }) recoverWith recoverFrom5XX(srn, startDate)
                    }
                  } else {
                    Future.successful(Redirect(navigator.nextPage(DeleteMemberPage, NormalMode, request.userAnswers, srn, startDate, accessType, version)))
                  }
              )
          case _ => Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
        }
      }
    }

  private def totalAmount: UserAnswers => BigDecimal =
    chargeServiceHelper.totalAmount(_, "chargeEDetails")
}
