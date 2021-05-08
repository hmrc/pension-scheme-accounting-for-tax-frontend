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
import models.{PenaltiesFilter, Quarters}
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
import uk.gov.hmrc.viewmodels.Text.Message
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
                                    renderer: Renderer
                                   )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  def onPageLoadAft(startDate: LocalDate, identifier: String, journeyType: PenaltiesFilter): Action[AnyContent] = (identify andThen allowAccess()).async {
    implicit request =>

      val title: Message = Message("penalties.aft.title").withArgs(
        LocalDate.parse(startDate).format(dateFormatterStartDate),
          Quarters.getQuarter(startDate).endDate.format(dateFormatterDMY))

      penaltiesService.getPenaltiesForJourney(request.psaIdOrException.id, journeyType).flatMap { penaltiesCache =>

        val chargeRefsIndex: String => String = cr => penaltiesCache.penalties.map(_.chargeReference).indexOf(cr).toString
        val json: Future[JsObject] = if (identifier.matches(srnRegex)) {
          schemeService.retrieveSchemeDetails(request.idOrException, identifier, "srn") map { schemeDetails =>

            val filteredPsaFS: Seq[PsaFS] = penaltiesCache.penalties
              .filter(_.pstr == schemeDetails.pstr)
              .filter(_.periodStartDate == startDate)
              .filter(p => getPenaltyType(p.chargeType) == AccountingForTaxPenalties)

            val penaltyTable: JsObject = penaltiesService.getPsaFsJson(filteredPsaFS, identifier, chargeRefsIndex, AccountingForTaxPenalties, journeyType)

            viewModel(title, penaltiesCache.psaName, schemeDetails.pstr, schemeAssociated = true, penaltyTable, schemeDetails.schemeName)
          }
        } else {
          penaltiesService.unassociatedSchemes(penaltiesCache.penalties, startDate, request.psaIdOrException.id) map { penalties =>

            val pstrs: Seq[String] = penalties.map(_.pstr)

            val filteredPsaFS: Seq[PsaFS] = penalties
              .filter(_.periodStartDate == startDate)
              .filter(p => getPenaltyType(p.chargeType) == AccountingForTaxPenalties)

            val penaltyTable: JsObject = penaltiesService.getPsaFsJson(filteredPsaFS, identifier, chargeRefsIndex, AccountingForTaxPenalties, journeyType)

            viewModel(title, penaltiesCache.psaName, pstrs(identifier.toInt), schemeAssociated = false, penaltyTable)
          }
        }

        json.flatMap(js => renderer.render(template = "financialStatement/penalties/penalties.njk", js).map(Ok(_)))

      }
  }

  def onPageLoadContract(year: String, identifier: String, journeyType: PenaltiesFilter): Action[AnyContent] = (identify andThen allowAccess()).async {
    implicit request =>
        onPageLoad(year.toInt, identifier, ContractSettlementCharges, journeyType)

  }

  def onPageLoadInfoNotice(year: String, identifier: String, journeyType: PenaltiesFilter): Action[AnyContent] = (identify andThen allowAccess()).async {
    implicit request =>
        onPageLoad(year.toInt, identifier, InformationNoticePenalties, journeyType)

  }

  def onPageLoadPension(year: String, identifier: String, journeyType: PenaltiesFilter): Action[AnyContent] = (identify andThen allowAccess()).async {
    implicit request =>
        onPageLoad(year.toInt, identifier, PensionsPenalties, journeyType)

  }

  private def onPageLoad(year: Int, identifier: String, penaltyType: PenaltyType, journeyType: PenaltiesFilter)
                        (implicit request: IdentifierRequest[AnyContent]): Future[Result] = {
    penaltiesService.getPenaltiesForJourney(request.psaIdOrException.id, journeyType).flatMap { penaltiesCache =>
      val title: Message = Message("penalties.nonAft.title", Message(s"penaltyType.${penaltyType.toString}"), year.toString)
      val chargeRefsIndex: String => String = cr => penaltiesCache.penalties.map(_.chargeReference).indexOf(cr).toString
      val json: Future[JsObject] = if (identifier.matches(srnRegex)) {
        schemeService.retrieveSchemeDetails(request.idOrException, identifier, "srn") map { schemeDetails =>

          val filteredPsaFS: Seq[PsaFS] = penaltiesCache.penalties
            .filter(_.pstr == schemeDetails.pstr)
            .filter(_.periodEndDate.getYear == year)
            .filter(p => getPenaltyType(p.chargeType) == penaltyType)

          val penaltyTable: JsObject = penaltiesService.getPsaFsJson(filteredPsaFS, identifier, chargeRefsIndex, penaltyType, journeyType)

          viewModel(title, penaltiesCache.psaName, schemeDetails.pstr, schemeAssociated = true, penaltyTable, schemeDetails.schemeName)
        }
      } else {
        penaltiesService.unassociatedSchemes(penaltiesCache.penalties, year, request.psaIdOrException.id) map { penalties =>

          val pstrs: Seq[String] = penalties.map(_.pstr)

          val filteredPsaFS: Seq[PsaFS] = penalties
            .filter(_.periodEndDate.getYear == year)
            .filter(p => getPenaltyType(p.chargeType) == penaltyType)

          val penaltyTable: JsObject = penaltiesService.getPsaFsJson(filteredPsaFS, identifier, chargeRefsIndex, penaltyType, journeyType)

          viewModel(title, penaltiesCache.psaName, pstrs(identifier.toInt), schemeAssociated = false, penaltyTable)
        }
      }

      json.flatMap(js => renderer.render(template = "financialStatement/penalties/penalties.njk", js).map(Ok(_)))

    }
  }

  private def viewModel(title: Message, psaName: String, pstr: String, schemeAssociated: Boolean, table: JsObject, args: String*)
                       (implicit messages: Messages): JsObject =
    Json.obj(
      "titleMessage" -> title.resolve,
      "psaName" -> psaName,
      "pstr" -> pstr,
      "schemeAssociated" -> schemeAssociated,
      "table" -> table,
      "schemeName" -> args
    )
}
