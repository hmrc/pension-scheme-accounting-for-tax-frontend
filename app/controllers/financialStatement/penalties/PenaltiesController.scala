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

package controllers.financialStatement.penalties

import config.Constants._
import controllers.actions._
import models.LocalDateBinder._
import models.Quarters
import models.financialStatement.PenaltyType._
import models.financialStatement.{PenaltyType, PsaFS}
import models.requests.IdentifierRequest
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import renderer.Renderer
import services.{PenaltiesService, SchemeService}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport
import utils.DateHelper.{dateFormatterDMY, dateFormatterStartDate}

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class PenaltiesController @Inject()(identify: IdentifierAction,
                                    allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                    override val messagesApi: MessagesApi,
                                    val controllerComponents: MessagesControllerComponents,
                                    penaltiesService: PenaltiesService,
                                    schemeService: SchemeService,
                                    renderer: Renderer,
                                    messages: Messages
                                   )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  def onPageLoadAft(startDate: String, identifier: String): Action[AnyContent] = (identify andThen allowAccess()).async {
    implicit request =>

      val title: String = messages("penalties.aft.title",
        LocalDate.parse(startDate).format(dateFormatterStartDate),
          Quarters.getQuarter(startDate).endDate.format(dateFormatterDMY))

      penaltiesService.getPenaltiesFromCache(request.psaIdOrException.id).flatMap { penalties =>

        val chargeRefsIndex: String => String = cr => penalties.map(_.chargeReference).indexOf(cr).toString
        val json: Future[JsObject] = if (identifier.matches(srnRegex)) {
          schemeService.retrieveSchemeDetails(request.idOrException, identifier, "srn") map { schemeDetails =>

            val filteredPsaFS: Seq[PsaFS] = penalties
              .filter(_.pstr == schemeDetails.pstr)
              .filter(_.periodStartDate == startDate)
              .filter(p => getPenaltyType(p.chargeType) == AccountingForTaxPenalties)

            val penaltyTable: JsObject = penaltiesService.getPsaFsJson(filteredPsaFS, identifier, chargeRefsIndex)

            viewModel(title, schemeDetails.pstr, schemeAssociated = true, penaltyTable, schemeDetails.schemeName)
          }
        } else {
          penaltiesService.unassociatedSchemes(penalties, startDate, request.psaIdOrException.id) map { penalties =>

            val pstrs: Seq[String] = penalties.map(_.pstr)

            val filteredPsaFS: Seq[PsaFS] = penalties
              .filter(_.periodStartDate == startDate)
              .filter(p => getPenaltyType(p.chargeType) == AccountingForTaxPenalties)

            val penaltyTable: JsObject = penaltiesService.getPsaFsJson(filteredPsaFS, identifier, chargeRefsIndex)

            viewModel(title, pstrs(identifier.toInt), schemeAssociated = false, penaltyTable)
          }
        }

        json.flatMap(js => renderer.render(template = "financialStatement/penalties/penalties.njk", js).map(Ok(_)))

      }
  }

  def onPageLoadContract(year: String, identifier: String): Action[AnyContent] = (identify andThen allowAccess()).async {
    implicit request =>
      penaltiesService.getPenaltiesFromCache(request.psaIdOrException.id).flatMap { penalties =>
        onPageLoad(year.toInt, identifier, penalties, "penaltyType.contractSettlement", ContractSettlementCharges)
      }
  }

  def onPageLoadInfoNotice(year: String, identifier: String): Action[AnyContent] = (identify andThen allowAccess()).async {
    implicit request =>
      penaltiesService.getPenaltiesFromCache(request.psaIdOrException.id).flatMap { penalties =>
        onPageLoad(year.toInt, identifier, penalties, "penaltyType.informationNotice", InformationNoticePenalties)
      }
  }

  def onPageLoadPension(year: String, identifier: String): Action[AnyContent] = (identify andThen allowAccess()).async {
    implicit request =>
      penaltiesService.getPenaltiesFromCache(request.psaIdOrException.id).flatMap { penalties =>
        onPageLoad(year.toInt, identifier, penalties, "penaltyType.pensionsPenalties", PensionsPenalties)
      }
  }

  private def onPageLoad(year: Int, identifier: String, penalties: Seq[PsaFS], typeParam: String, penaltyType: PenaltyType)
                        (implicit request: IdentifierRequest[AnyContent]): Future[Result] = {

    val title: String = messages("penalties.nonAft.title", typeParam, year)
    val chargeRefsIndex: String => String = cr => penalties.map(_.chargeReference).indexOf(cr).toString
    val json: Future[JsObject] = if (identifier.matches(srnRegex)) {
      schemeService.retrieveSchemeDetails(request.idOrException, identifier, "srn") map { schemeDetails =>

        val filteredPsaFS: Seq[PsaFS] = penalties
          .filter(_.pstr == schemeDetails.pstr)
          .filter(_.periodStartDate.getYear == year)
          .filter(p => getPenaltyType(p.chargeType) == penaltyType)

        val penaltyTable: JsObject = penaltiesService.getPsaFsJson(filteredPsaFS, identifier, chargeRefsIndex)

        viewModel(title, schemeDetails.pstr, schemeAssociated = true, penaltyTable, schemeDetails.schemeName)
      }
    } else {
      penaltiesService.unassociatedSchemes(penalties, year, request.psaIdOrException.id) map { penalties =>

        val pstrs: Seq[String] = penalties.map(_.pstr)

        val filteredPsaFS: Seq[PsaFS] = penalties
          .filter(_.periodStartDate.getYear == year)
          .filter(p => getPenaltyType(p.chargeType) == penaltyType)

        val penaltyTable: JsObject = penaltiesService.getPsaFsJson(filteredPsaFS, identifier, chargeRefsIndex)

        viewModel(title, pstrs(identifier.toInt), schemeAssociated = false, penaltyTable)
      }
    }

    json.flatMap(js => renderer.render(template = "financialStatement/penalties/penalties.njk", js).map(Ok(_)))

  }

  private def viewModel(title: String, pstr: String, schemeAssociated: Boolean, table: JsObject, args: String*): JsObject =
    Json.obj(
      "title" -> title,
      "pstr" -> pstr,
      "schemeAssociated" -> schemeAssociated,
      "table" -> table,
      "schemeName" -> args
    )
}
