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

package controllers

import audit.{AFTReturnEmailAuditEvent, AuditService}
import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import connectors.{EmailConnector, EmailStatus, ReturnAlreadySubmittedException}
import controllers.actions._
import helpers.ErrorHelper.recoverFrom5XX
import models.AdministratorOrPractitioner._
import models.JourneyType.{AFT_SUBMIT_AMEND, AFT_SUBMIT_RETURN}
import models.LocalDateBinder._
import models.ValueChangeType.{ChangeTypeDecrease, ChangeTypeIncrease, ChangeTypeSame}
import models.requests.DataRequest
import models.{AFTQuarter, AccessType, Declaration, NormalMode}
import navigators.CompoundNavigator
import pages.{ConfirmSubmitAFTAmendmentValueChangeTypePage, DeclarationPage, NameQuery}
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.AFTService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.DateHelper.{dateFormatterDMY, dateFormatterStartDate, formatSubmittedDate}

import java.time.{LocalDate, ZoneId, ZonedDateTime}
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import views.html.{DeclarationView, PspDeclarationView}

class DeclarationController @Inject()(
    override val messagesApi: MessagesApi,
    identify: IdentifierAction,
    getData: DataRetrievalAction,
    requireData: DataRequiredAction,
    allowAccess: AllowAccessActionProvider,
    allowSubmission: AllowSubmissionAction,
    aftService: AFTService,
    userAnswersCacheConnector: UserAnswersCacheConnector,
    navigator: CompoundNavigator,
    val controllerComponents: MessagesControllerComponents,
    config: FrontendAppConfig,
    declarationView: DeclarationView,
    pspDeclarationView: PspDeclarationView,
    emailConnector: EmailConnector,
    auditService: AuditService
)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport {
  def onPageLoad(srn: String, startDate: LocalDate, accessType: AccessType, version: Int): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate, None, version, accessType)
      andThen allowSubmission).async { implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>

        val view = request.schemeAdministratorType match {
          case Administrator => declarationView(
            routes.DeclarationController.onSubmit(srn, startDate, accessType, version).url,
            controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
            schemeName
          )
          case Practitioner => pspDeclarationView(
            routes.DeclarationController.onSubmit(srn, startDate, accessType, version).url,
            controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
            schemeName
          )
        }

        Future.successful(Ok(view))
      }
    }

  def onSubmit(srn: String, startDate: LocalDate, accessType: AccessType, version: Int): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate, None, version, accessType)
      andThen allowSubmission).async { implicit request =>
      DataRetrievals.retrievePSAAndSchemeDetailsWithAmendment { (schemeName, pstr, email, quarter, isAmendment, amendedVersion) =>
        val schemeAdministratorType = if (request.schemeAdministratorType == Administrator) "PSA" else "PSP"
        val declaration = Declaration(schemeAdministratorType, request.idOrException, hasAgreed = true)
        (for {
          answersWithDeclaration <- Future.fromTry(request.userAnswers.set(DeclarationPage, declaration))
          _ <- userAnswersCacheConnector.savePartial(request.internalId, answersWithDeclaration.data)
          _ <- aftService.fileSubmitReturn(pstr, answersWithDeclaration)
          _ <- sendEmail(email, quarter, schemeName, isAmendment, amendedVersion)
        } yield {
          Redirect(navigator.nextPage(DeclarationPage, NormalMode, request.userAnswers, srn, startDate, accessType, version))
        }) recoverWith {
          case ReturnAlreadySubmittedException() =>
            Future.successful(Redirect(controllers.routes.CannotSubmitAFTController.onPageLoad(srn, startDate)))
        } recoverWith recoverFrom5XX(srn, startDate)
      }
    }

  private def sendEmail(email: String, quarter: AFTQuarter, schemeName: String, isAmendment: Boolean, amendedVersion: Int)(
      implicit request: DataRequest[_], hc: HeaderCarrier, messages: Messages): Future[EmailStatus] = {
    val requestId = hc.requestId.map(_.value).getOrElse(request.headers.get("X-Session-ID").getOrElse(""))
    val name = request.userAnswers.getOrException(NameQuery)

    val quarterStartDate = quarter.startDate.format(dateFormatterStartDate)
    val quarterEndDate = quarter.endDate.format(dateFormatterDMY)
    val submittedDate = formatSubmittedDate(ZonedDateTime.now(ZoneId.of("Europe/London")))

    val sendToEmailId = messages("confirmation.whatNext.send.to.email.id")
    val accountingPeriod = messages("confirmation.table.accounting.period.value", quarterStartDate, quarterEndDate)

    val templateParams = Map(
      "schemeName" -> schemeName,
      "accountingPeriod" -> accountingPeriod,
      "dateSubmitted" -> submittedDate,
      "hmrcEmail" -> sendToEmailId,
      "psaName" -> name
    ) ++ (if (isAmendment) Map("submissionNumber" -> s"$amendedVersion") else Map.empty)

    val journeyType = if (isAmendment) {
      AFT_SUBMIT_AMEND
    } else {
      AFT_SUBMIT_RETURN
    }

    emailConnector.sendEmail(request.schemeAdministratorType, requestId, request.idOrException, journeyType, email, templateId, templateParams)
      .map{ emailStatus =>
        auditService.sendEvent(AFTReturnEmailAuditEvent(request.idOrException, journeyType, request.schemeAdministratorType, email))
        emailStatus
      }
  }

  private def templateId(implicit request: DataRequest[_]): String ={
    (request.isAmendment, request.userAnswers.get(ConfirmSubmitAFTAmendmentValueChangeTypePage)) match{
      case (true, Some(ChangeTypeDecrease)) => config.amendAftReturnDecreaseTemplateIdId
      case (true, Some(ChangeTypeIncrease)) => config.amendAftReturnIncreaseTemplateIdId
      case (true, Some(ChangeTypeSame)) => config.amendAftReturnNoChangeTemplateIdId
      case _ => config.fileAFTReturnTemplateId
    }
  }

}
