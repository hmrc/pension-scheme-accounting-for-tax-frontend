/*
 * Copyright 2021 HM Revenue & Customs
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

import java.time.LocalDate

import config.FrontendAppConfig
import connectors.AFTConnector
import controllers.DataRetrievals
import controllers.actions._
import helpers.AmendmentHelper
import javax.inject.Inject
import models.LocalDateBinder._
import models.viewModels.ViewAmendmentDetails
import models.{GenericViewModel, UserAnswers, AccessType, Draft, AmendedChargeStatus}
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.{Json, JsObject}
import play.api.mvc._
import renderer.Renderer
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport
import uk.gov.hmrc.viewmodels.Text.Literal
import viewmodels.Table
import viewmodels.Table.Cell

import scala.concurrent.ExecutionContext
import models.AccessMode.PageAccessModeCompile
import pages.ViewOnlyAccessiblePage

class ViewAllAmendmentsController @Inject()(override val messagesApi: MessagesApi,
                                            identify: IdentifierAction,
                                            getData: DataRetrievalAction,
                                            allowAccess: AllowAccessActionProvider,
                                            requireData: DataRequiredAction,
                                            val controllerComponents: MessagesControllerComponents,
                                            config: FrontendAppConfig,
                                            aftConnector: AFTConnector,
                                            amendmentHelper: AmendmentHelper,
                                            renderer: Renderer)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  def onPageLoad(srn: String, startDate: LocalDate, accessType: AccessType, version: Int): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen
      requireData andThen allowAccess(srn, startDate, Some(ViewOnlyAccessiblePage), version, accessType)).async { implicit request =>
      DataRetrievals.retrieveSchemeWithPSTR { (schemeName, pstr) =>
        val updatedVersion = if(request.isPrecompile) version - 1 else version
        val previousVersion = if(request.isPrecompile) version - 2 else version - 1

        aftConnector.getAFTDetails(pstr, startDate, aftVersion = s"$previousVersion").flatMap { previousUaJsValue =>
          aftConnector.getAFTDetails(pstr, startDate, aftVersion = s"$updatedVersion").flatMap { currentUaJsValue =>
            val currentAnswers = UserAnswers(currentUaJsValue.as[JsObject])
            val previousAnswers = UserAnswers(previousUaJsValue.as[JsObject])

            val viewModel = GenericViewModel(
              submitUrl = controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, Draft, version).url,
              returnUrl = config.schemeDashboardUrl(request).format(srn),
              schemeName = schemeName
            )

            val json = Json.obj(
              fields = "srn" -> srn,
              "startDate" -> Some(startDate),
              "viewModel" -> viewModel,
              "versionNumber" -> updatedVersion,
              "isDraft" -> (request.sessionData.sessionAccessData.accessMode == PageAccessModeCompile),
              "addedTable" -> mapToTable(
                caption = "added",
                amendmentHelper.getAllAmendments(currentAnswers, previousAnswers, updatedVersion).filter(_.status == AmendedChargeStatus.Added)),
              "deletedTable" -> mapToTable(
                caption = "deleted",
                amendmentHelper.getAllAmendments(currentAnswers, previousAnswers, updatedVersion).filter(_.status == AmendedChargeStatus.Deleted)),
              "updatedTable" -> mapToTable(
                caption = "updated",
                amendmentHelper.getAllAmendments(currentAnswers, previousAnswers, updatedVersion).filter(_.status == AmendedChargeStatus.Updated))
            )

            renderer.render(template = "amend/viewAllAmendments.njk", json).map(Ok(_))
          }
        }
      }
    }

  private def mapToTable(caption: String, allAmendments: Seq[ViewAmendmentDetails])(implicit messages: Messages): Table = {

    val head = Seq(
      Cell(msg"allAmendments.memberDetails.h1", classes = Seq("govuk-!-width-one-quarter")),
      Cell(msg"allAmendments.chargeType.h1", classes = Seq("govuk-!-width-one-quarter")),
      Cell(msg"allAmendments.chargeAmount.h1", classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric", "govuk-!-font-weight-bold"))
    )

    val rows = allAmendments.map { data =>
      Seq(
        Cell(Literal(data.memberDetails), classes = Seq("govuk-!-width-one-quarter"), attributes = Map("role" -> "cell")),
        Cell(msg"allAmendments.charge.type.${data.chargeType}", classes = Seq("govuk-!-width-one-quarter"), attributes = Map("role" -> "cell")),
        Cell(Literal(data.chargeAmount), classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric"), attributes = Map("role" -> "cell"))
      )
    }

    Table(
      head = head,
      rows = rows,
      attributes = Map("role" -> "table", "aria-describedby" -> messages(s"allAmendments.table.caption.$caption").toLowerCase )
    )

  }
}
