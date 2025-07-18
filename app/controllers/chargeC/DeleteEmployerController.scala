/*
 * Copyright 2024 HM Revenue & Customs
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

import controllers.DataRetrievals
import controllers.actions._
import forms.YesNoFormProvider
import helpers.ChargeServiceHelper
import helpers.ErrorHelper.recoverFrom5XX
import models.LocalDateBinder._
import models.SponsoringEmployerType.{SponsoringEmployerTypeIndividual, SponsoringEmployerTypeOrganisation}
import models.requests.DataRequest
import models.{AccessType, Index, NormalMode, UserAnswers}
import navigators.CompoundNavigator
import pages.chargeC._
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.{DeleteAFTChargeService, UserAnswersService}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import views.html.chargeC.DeleteEmployerView

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class DeleteEmployerController @Inject()(override val messagesApi: MessagesApi,
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
                                         view: DeleteEmployerView)(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport {

  private def form(memberName: String)(implicit messages: Messages): Form[Boolean] =
    formProvider(messages("deleteEmployer.chargeC.error.required", memberName))

  def onPageLoad(srn: String, startDate: LocalDate, accessType: AccessType, version: Int, index: Index): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate, None, version, accessType)).async { implicit request =>
      DataRetrievals.retrieveSchemeEmployerTypeAndSponsoringEmployer(index) { (schemeName, employerName, employerType) =>
        val returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url
        val submitCall = routes.DeleteEmployerController.onSubmit(srn, startDate, accessType, version, index)

        Future.successful(Ok(view(form(employerName),schemeName,  submitCall, returnUrl, employerName,
          Messages(s"chargeC.employerType.${employerType.toString}"),
          utils.Radios.yesNo(form(employerName)(implicitly)("value")))))
      }
    }

  def onSubmit(srn: String, startDate: LocalDate, accessType: AccessType, version: Int, index: Index): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData).async { implicit request =>
      DataRetrievals.retrieveSchemeEmployerTypeAndSponsoringEmployer(index) { (schemeName, employerName, employerType) =>
        form(employerName)
          .bindFromRequest()
          .fold(
            formWithErrors => {
              val returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url
              val submitCall = routes.DeleteEmployerController.onSubmit(srn, startDate, accessType, version, index)

              Future.successful(BadRequest(view(formWithErrors,schemeName,  submitCall, returnUrl, employerName,
                Messages(s"chargeC.employerType.${employerType.toString}"),
                utils.Radios.yesNo(form(employerName)(implicitly)("value")))))

            },
            value =>
              if (value) {
                DataRetrievals.retrievePSTR { pstr =>

                  (for {
                    updatedAnswers <- Future.fromTry(removeCharge(index))
                    _ <- deleteAFTChargeService.deleteAndFileAFTReturn(pstr, updatedAnswers, srn)
                  } yield {
                    Redirect(navigator.nextPage(DeleteEmployerPage, NormalMode, updatedAnswers, srn, startDate, accessType, version))
                  }) recoverWith recoverFrom5XX(srn, startDate)
                }
              } else {
                Future.successful(Redirect(navigator.nextPage(DeleteEmployerPage, NormalMode, request.userAnswers, srn, startDate, accessType, version)))
              }
          )
      }
    }
  private def removeCharge(index: Int)
                          (implicit request: DataRequest[AnyContent]): Try[UserAnswers] = {
    val ua = request.userAnswers
    (ua.get(WhichTypeOfSponsoringEmployerPage(index)),
      ua.get(SponsoringIndividualDetailsPage(index)),
      ua.get(SponsoringOrganisationDetailsPage(index))) match {

      case (Some(SponsoringEmployerTypeIndividual), Some(_), _) =>
        userAnswersService.removeMemberBasedCharge(SponsoringIndividualDetailsPage(index), totalAmount)

      case (Some(SponsoringEmployerTypeOrganisation), _, Some(_)) =>
        userAnswersService.removeMemberBasedCharge(SponsoringOrganisationDetailsPage(index), totalAmount)

      case _ => Try(ua)
    }
  }

  private def totalAmount : UserAnswers => BigDecimal = {
    chargeServiceHelper.totalAmount(_, "chargeCDetails")
  }

  case object EmployerTypeUnidentified extends Exception("Employer did not match individual or organisation type")
}
