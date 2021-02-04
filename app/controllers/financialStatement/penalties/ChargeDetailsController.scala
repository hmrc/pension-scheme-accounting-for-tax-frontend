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
import connectors.FinancialStatementConnector
import connectors.cache.FinancialInfoCacheConnector
import controllers.actions.IdentifierAction
import models.Quarters.getQuarter
import models.financialStatement.PsaFS
import models.requests.IdentifierRequest
import play.api.Logger
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.{PenaltiesService, SchemeService}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport
import utils.DateHelper.{dateFormatterDMY, dateFormatterStartDate}

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ChargeDetailsController @Inject()(
                                         identify: IdentifierAction,
                                         override val messagesApi: MessagesApi,
                                         val controllerComponents: MessagesControllerComponents,
                                         fsConnector: FinancialStatementConnector,
                                         fiCacheConnector: FinancialInfoCacheConnector,
                                         penaltiesService: PenaltiesService,
                                         schemeService: SchemeService,
                                         renderer: Renderer
                                       )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  private val logger = Logger(classOf[ChargeDetailsController])

  def onPageLoad(identifier: String, startDate: LocalDate, chargeReferenceIndex: String): Action[AnyContent] = identify.async {
    implicit request =>
      fsConnector.getPsaFS(request.psaIdOrException.id).flatMap {
        psaFS =>
          val filteredPsaFS = psaFS.filter(_.periodStartDate == startDate)
          fiCacheConnector.fetch flatMap {
            case Some(jsValue) =>
              val chargeRefs: Seq[String] = jsValue.as[Seq[PsaFS]].map(_.chargeReference)
              try {
                psaFS.find(_.chargeReference == chargeRefs(chargeReferenceIndex.toInt)) match {
                  case Some(fs) =>
                    if (filteredPsaFS.nonEmpty) {
                      if (identifier.matches(srnRegex)) {
                        schemeService.retrieveSchemeDetails(
                          psaId = request.idOrException,
                          srn = identifier,
                          schemeIdType = "srn"
                        ) flatMap {
                          schemeDetails =>
                            val json = Json.obj(
                              "schemeAssociated" -> true,
                              "schemeName" -> schemeDetails.schemeName
                            ) ++ commonJson(fs, psaFS, chargeRefs, chargeReferenceIndex, startDate)

                            renderer.render(template = "financialStatement/chargeDetails.njk", json).map(Ok(_))
                        }
                      } else {
                        val json = Json.obj(
                          "schemeAssociated" -> false
                        ) ++ commonJson(fs, psaFS, chargeRefs, chargeReferenceIndex, startDate)

                        renderer.render(template = "financialStatement/chargeDetails.njk", json).map(Ok(_))
                      }
                    } else {
                      Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
                    }
                  case _ =>
                    Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
                }
              } catch {
                case _: IndexOutOfBoundsException =>
                  logger.warn(
                    s"[financialStatement.ChargeDetailsController][IndexOutOfBoundsException]:" +
                      s"index $chargeReferenceIndex of collection length ${chargeRefs.length} attempted"
                  )
                  Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
              }
            case _ =>
              Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
          }

      }
  }

  private def commonJson(
                          fs: PsaFS,
                          psaFS: Seq[PsaFS],
                          chargeRefs: Seq[String],
                          chargeReferenceIndex: String,
                          startDate: LocalDate
                        )(implicit request: IdentifierRequest[AnyContent]): JsObject =
    Json.obj(
      "heading" ->
        heading(psaFS.filter(_.chargeReference == chargeRefs(chargeReferenceIndex.toInt)).head.chargeType.toString),
      "isOverdue" ->
        penaltiesService.isPaymentOverdue(psaFS.filter(_.chargeReference == chargeRefs(chargeReferenceIndex.toInt)).head),
      "period" ->
        Messages("penalties.period",
          startDate.format(dateFormatterStartDate),
          getQuarter(startDate).endDate.format(dateFormatterDMY)
        ),
      "chargeReference" ->
        fs.chargeReference,
      "list" ->
        penaltiesService.chargeDetailsRows(psaFS.filter(_.chargeReference == chargeRefs(chargeReferenceIndex.toInt)).head)
    )

  private val heading: String => String = s => if (s.contains('(')) s.substring(0, s.indexOf('(')) else s
}
