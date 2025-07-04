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

package controllers.amend

import config.FrontendAppConfig
import connectors.AFTConnector
import controllers.DataRetrievals
import controllers.actions._
import helpers.AmendmentHelper
import models.AccessMode.PageAccessModeCompile
import models.LocalDateBinder._
import models.viewModels.ViewAmendmentDetails
import models.{AccessType, AmendedChargeStatus, Draft, UserAnswers}
import pages.ViewOnlyAccessiblePage
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.JsObject
import play.api.mvc._
import uk.gov.hmrc.govukfrontend.views.Aliases.Text
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.govukfrontend.views.viewmodels.table.{HeadCell, Table, TableRow}

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import views.html.amend.ViewAllAmendmentsView

class ViewAllAmendmentsController @Inject()(override val messagesApi: MessagesApi,
                                            identify: IdentifierAction,
                                            getData: DataRetrievalAction,
                                            allowAccess: AllowAccessActionProvider,
                                            requireData: DataRequiredAction,
                                            val controllerComponents: MessagesControllerComponents,
                                            config: FrontendAppConfig,
                                            aftConnector: AFTConnector,
                                            amendmentHelper: AmendmentHelper,
                                            viewAllAmendmentsView: ViewAllAmendmentsView)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport {

  def onPageLoad(srn: String, startDate: LocalDate, accessType: AccessType, version: Int): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen
      requireData andThen allowAccess(srn, startDate, Some(ViewOnlyAccessiblePage), version, accessType)).async { implicit request =>
      DataRetrievals.retrieveSchemeWithPSTR { (schemeName, pstr) =>
        val updatedVersion = if(request.isPrecompile) version - 1 else version
        val previousVersion = if(request.isPrecompile) version - 2 else version - 1

        aftConnector.getAFTDetails(pstr, startDate, aftVersion = s"$previousVersion", srn, request.isLoggedInAsPsa).flatMap { previousUaJsValue =>
          aftConnector.getAFTDetails(pstr, startDate, aftVersion = s"$updatedVersion", srn, request.isLoggedInAsPsa).flatMap { currentUaJsValue =>
            val currentAnswers = UserAnswers(currentUaJsValue.as[JsObject])
            val previousAnswers = UserAnswers(previousUaJsValue.as[JsObject])
            val isDraft = request.sessionData.sessionAccessData.accessMode == PageAccessModeCompile

            val pageTitle = if (isDraft) {
              Messages("allAmendments.draft.title")
            } else {
              Messages("allAmendments.submission.title")
            }

            Future.successful(Ok(viewAllAmendmentsView(
              pageTitle = pageTitle,
              isDraft = isDraft,
              versionNumber = updatedVersion,
              addedTable = mapToTable(
                caption = "added",
                amendmentHelper.getAllAmendments(currentAnswers, previousAnswers, updatedVersion).filter(_.status == AmendedChargeStatus.Added)),
              deletedTable = mapToTable(
                caption = "deleted",
                amendmentHelper.getAllAmendments(currentAnswers, previousAnswers, updatedVersion).filter(_.status == AmendedChargeStatus.Deleted)),
              updatedTable = mapToTable(
                caption = "updated",
                amendmentHelper.getAllAmendments(currentAnswers, previousAnswers, updatedVersion).filter(_.status == AmendedChargeStatus.Updated)),
              submitUrl = controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, Draft, version).url,
              returnUrl = config.schemeDashboardUrl(request).replace("%s", srn),
              schemeName = schemeName
            )))
          }
        }
      }
    }

  private def mapToTable(caption: String, allAmendments: Seq[ViewAmendmentDetails])(implicit messages: Messages): Table = {

    val head = Seq(
      HeadCell(Text(Messages("allAmendments.memberDetails.h1")), classes = "govuk-!-width-one-quarter"),
      HeadCell(Text(Messages("allAmendments.chargeType.h1")), classes = "govuk-!-width-one-quarter"),
      HeadCell(Text(Messages("allAmendments.chargeAmount.h1")), classes = "govuk-!-width-one-quarter govuk-table__cell--numeric govuk-!-font-weight-bold")
    )

    val rows = allAmendments.map { data =>
      Seq(
        TableRow(Text(data.memberDetails), classes = "govuk-!-width-one-quarter", attributes = Map("role" -> "cell")),
        TableRow(Text(Messages(s"allAmendments.charge.type.${data.chargeType}")), classes = "govuk-!-width-one-quarter", attributes = Map("role" -> "cell")),
        TableRow(Text(data.chargeAmount), classes = "govuk-!-width-one-quarter govuk-table__cell--numeric", attributes = Map("role" -> "cell"))
      )
    }

    Table(
      rows = rows,
      head = Some(head),
      attributes = Map("role" -> "table", "aria-describedby" -> messages(s"allAmendments.table.caption.$caption").toLowerCase )
    )

  }
}
