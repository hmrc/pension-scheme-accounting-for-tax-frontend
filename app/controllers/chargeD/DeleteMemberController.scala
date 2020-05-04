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

package controllers.chargeD

import java.time.LocalDate

import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import controllers.DataRetrievals
import controllers.actions._
import forms.DeleteFormProvider
import helpers.ChargeDHelper.getLifetimeAllowanceMembers
import javax.inject.Inject
import models.LocalDateBinder._
import models.{GenericViewModel, Index, NormalMode, UserAnswers}
import navigators.CompoundNavigator
import pages.chargeD.{DeleteMemberPage, MemberDetailsPage, TotalChargeAmountPage}
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.{AFTService, UserAnswersService}
import services.DeleteAFTChargeService
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.{NunjucksSupport, Radios}

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
                                       formProvider: DeleteFormProvider,
                                       val controllerComponents: MessagesControllerComponents,
                                       config: FrontendAppConfig,
                                       renderer: Renderer)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  private def form(memberName: String)(implicit messages: Messages): Form[Boolean] =
    formProvider(messages("deleteMember.chargeD.error.required", memberName))

  def onPageLoad(srn: String, startDate: LocalDate, index: Index): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate)).async { implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>
        request.userAnswers.get(MemberDetailsPage(index)) match {
          case Some(memberDetails) =>
            val viewModel = GenericViewModel(
              submitUrl = routes.DeleteMemberController.onSubmit(srn, startDate, index).url,
              returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate).url,
              schemeName = schemeName
            )

            val json = Json.obj(
              "srn" -> srn,
              "startDate" -> Some(startDate),
              "form" -> form(memberDetails.fullName),
              "viewModel" -> viewModel,
              "radios" -> Radios.yesNo(form(memberDetails.fullName)(implicitly)("value")),
              "memberName" -> memberDetails.fullName
            )

            renderer.render("chargeD/deleteMember.njk", json).map(Ok(_))
          case _ => Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
        }
      }
    }

  def onSubmit(srn: String, startDate: LocalDate, index: Index): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData).async { implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>
        request.userAnswers.get(MemberDetailsPage(index)) match {
          case Some(memberDetails) =>
            form(memberDetails.fullName)
              .bindFromRequest()
              .fold(
                formWithErrors => {

                  val viewModel = GenericViewModel(
                    submitUrl = routes.DeleteMemberController.onSubmit(srn, startDate, index).url,
                    returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate).url,
                    schemeName = schemeName
                  )

                  val json = Json.obj(
                    "srn" -> srn,
                    "startDate" -> Some(startDate),
                    "form" -> formWithErrors,
                    "viewModel" -> viewModel,
                    "radios" -> Radios.yesNo(formWithErrors("value")),
                    "memberName" -> memberDetails.fullName
                  )

                  renderer.render("chargeD/deleteMember.njk", json).map(BadRequest(_))

                },
                value =>
                  if (value) {
                    DataRetrievals.retrievePSTR {
                      pstr =>
                        for {
                          interimAnswers <- Future.fromTry(request.userAnswers.set(MemberDetailsPage(index), memberDetails.copy(isDeleted = true))
                          .flatMap(answers => answers.set(TotalChargeAmountPage, totalAmount(answers, srn, startDate))))

                          updatedAnswers <- Future.fromTry(userAnswersService.set(MemberDetailsPage(index), interimAnswers))

                          _ <- userAnswersCacheConnector.save(request.internalId, updatedAnswers.data)
                          _ <- deleteAFTChargeService.deleteAndFileAFTReturn(pstr, updatedAnswers)
                        } yield Redirect(navigator.nextPage(DeleteMemberPage, NormalMode, updatedAnswers, srn, startDate))
                    }
                  } else {
                    Future.successful(Redirect(navigator.nextPage(DeleteMemberPage, NormalMode, request.userAnswers, srn, startDate)))
                }
              )
          case _ => Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
        }
      }
    }

  def totalAmount(ua: UserAnswers, srn: String, startDate: LocalDate): BigDecimal = getLifetimeAllowanceMembers(ua, srn, startDate).map(_.amount).sum
}
