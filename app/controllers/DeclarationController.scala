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

import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.LocalDate

import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import connectors.EmailConnector
import models.SchemeAdministratorType._
import connectors.EmailStatus
import controllers.actions._
import javax.inject.Inject
import models.LocalDateBinder._
import models.requests.DataRequest
import models.AccessType
import models.Declaration
import models.GenericViewModel
import models.NormalMode
import models.Quarter
import models.ValueChangeType.ChangeTypeDecrease
import models.ValueChangeType.ChangeTypeIncrease
import models.ValueChangeType.ChangeTypeSame
import navigators.CompoundNavigator
import pages.ConfirmSubmitAFTAmendmentValueChangeTypePage
import pages.DeclarationPage
import pages.PSANameQuery
import play.api.i18n.I18nSupport
import play.api.i18n.Messages
import play.api.i18n.MessagesApi
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.MessagesControllerComponents
import renderer.Renderer
import services.AFTService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import utils.DateHelper.dateFormatterDMY
import utils.DateHelper.dateFormatterStartDate
import utils.DateHelper.formatSubmittedDate

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

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
    renderer: Renderer,
    emailConnector: EmailConnector
)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport {
  def onPageLoad(srn: String, startDate: LocalDate, accessType: AccessType, version: Int): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate, None, version, accessType)
      andThen allowSubmission).async { implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>
        val viewModel = GenericViewModel(
          submitUrl = routes.DeclarationController.onSubmit(srn, startDate, accessType, version).url,
          returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
          schemeName = schemeName
        )

        val template = request.schemeAdministratorType match {
          case SchemeAdministratorTypePSA => "declaration.njk"
          case SchemeAdministratorTypePSP => "pspDeclaration.njk"
        }
        renderer.render(template, Json.obj(fields = "viewModel" -> viewModel)).map(Ok(_))
      }
    }

  def onSubmit(srn: String, startDate: LocalDate, accessType: AccessType, version: Int): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate, None, version, accessType)
      andThen allowSubmission).async { implicit request =>
      DataRetrievals.retrievePSAAndSchemeDetailsWithAmendment { (schemeName, pstr, email, quarter, isAmendment, amendedVersion) =>
        val declaration = Declaration(request.schemeAdministratorType.toString, request.idOrException, hasAgreed = true)
        for {
          answersWithDeclaration <- Future.fromTry(request.userAnswers.set(DeclarationPage, declaration))
          _ <- userAnswersCacheConnector.save(request.internalId, answersWithDeclaration.data)
          _ <- aftService.fileSubmitReturn(pstr, answersWithDeclaration)
          _ <- sendEmail(email, quarter, schemeName, isAmendment, amendedVersion)
        } yield {
          Redirect(navigator.nextPage(DeclarationPage, NormalMode, request.userAnswers, srn, startDate, accessType, version))
        }
      }
    }

  private def sendEmail(email: String, quarter: Quarter, schemeName: String, isAmendment: Boolean, amendedVersion: Int)(
      implicit request: DataRequest[_],
      hc: HeaderCarrier,
      messages: Messages): Future[EmailStatus] = {
    val requestId = hc.requestId.map(_.value).getOrElse(request.headers.get("X-Session-ID").getOrElse(""))
    val psaName = request.userAnswers.getOrException(PSANameQuery)

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
      "psaName" -> psaName
    ) ++ (if (isAmendment) Map("submissionNumber" -> s"$amendedVersion") else Map.empty)

    val journeyType = if (isAmendment) {
      "AFTAmendmentSubmitted"
    } else {
      "AFTReturnSubmitted"
    }

    emailConnector.sendEmail(requestId, request.idOrException, journeyType, email, templateId, templateParams)
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
