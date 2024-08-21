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

import connectors.AFTConnector
import controllers.actions.{AllowAccessActionProviderForIdentifierRequest, IdentifierAction}
import models.AFTQuarter.formatForDisplayOneYear
import models.viewModels.PastAftReturnsViewModel
import models.{AFTOverview, PastAftReturnGroup, Quarters, ReportLink}
import play.api.i18n.I18nSupport
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.SchemeService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class PastAftReturnsController @Inject()(aftConnector: AFTConnector,
                                         allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                         val controllerComponents: MessagesControllerComponents,
                                         identify: IdentifierAction,
                                         renderer: Renderer,
                                         schemeService: SchemeService
                                   )(implicit ec: ExecutionContext) extends FrontendBaseController
  with I18nSupport
  with NunjucksSupport {

  def onPageLoad(srn: String, page: Int): Action[AnyContent] = (identify andThen allowAccess(Some(srn))).async {
    implicit request =>
      schemeService.retrieveSchemeDetails(request.idOrException, srn, "srn").flatMap { schemeDetails =>
        aftConnector.getAftOverview(schemeDetails.pstr).flatMap { aftOverview =>
          val schemeName = schemeDetails.schemeName

          val currentYear = LocalDate.now().getYear

          val startDateRange = Range.inclusive(currentYear - 7, currentYear).toList.reverse

          val groupedReturns = getGroupedReturns(srn, startDateRange, aftOverview)

          val ctxValues = getCtx(groupedReturns, page, schemeName, srn)

          renderer.render(
            "past_aft_returns.njk",
            ctx = ctxValues
          ).map(Ok(_))
        }
      }
  }


  private def getGroupedReturns(srn: String, startDateRange: List[Int], aftOverview: Seq[AFTOverview]): List[PastAftReturnGroup] = {
    val groupedReturns: List[PastAftReturnGroup] = startDateRange.map(startDate => {
      val returnsFromTaxYear = aftOverview.filter(aftReturn => aftReturn.periodStartDate.getYear == startDate)

      val reportLinks = returnsFromTaxYear.map(aftReturn => ReportLink(
        formatForDisplayOneYear(Quarters.getQuarter(aftReturn.periodStartDate)),
        controllers.amend.routes.ReturnHistoryController.onPageLoad(srn, aftReturn.periodStartDate.toString).url
      )).toList

      if (returnsFromTaxYear.nonEmpty) {
        PastAftReturnGroup(s"$startDate", reportLinks)
      } else {
        PastAftReturnGroup(s"$startDate", List.empty)
      }
    })

    groupedReturns.filter(pastReturn => pastReturn.reports.nonEmpty)
  }

  private def getCtx(groupedReturns: List[PastAftReturnGroup], page: Int, schemeName: String, srn: String): JsObject = {
    val (pageNo, viewModel) = if (groupedReturns.size > 4) {
      val splitReturns = groupedReturns.splitAt(4)
      val firstPageReturns = splitReturns._1
      val secondPageReturns = splitReturns._2

      if (page == 2) {
        (2, PastAftReturnsViewModel(secondPageReturns))
      } else {
        (1, PastAftReturnsViewModel(firstPageReturns))
      }
    } else {
      (0, PastAftReturnsViewModel(groupedReturns))
    }

    Json.obj(
      "page" -> pageNo,
      "schemeName" -> schemeName,
      "returnLink" -> controllers.routes.AFTOverviewController.onPageLoad(srn).url,
      "viewModel" -> viewModel,
      "firstPageLink" -> controllers.routes.PastAftReturnsController.onPageLoad(srn, 1).url,
      "secondPageLink" -> controllers.routes.PastAftReturnsController.onPageLoad(srn, 2).url
    )
  }
}
