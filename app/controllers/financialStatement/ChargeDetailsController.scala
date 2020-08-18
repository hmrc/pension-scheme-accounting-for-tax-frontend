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

package controllers.financialStatement

import java.time.LocalDate

import config.FrontendAppConfig
import connectors.FinancialStatementConnector
import controllers.actions.IdentifierAction
import javax.inject.Inject
import models.Quarters.getQuarter
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.{PenaltiesService, SchemeService}
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport
import utils.DateHelper.{dateFormatterDMY, dateFormatterStartDate}

import scala.concurrent.{ExecutionContext, Future}

class ChargeDetailsController @Inject()(appConfig: FrontendAppConfig,
                                        identify: IdentifierAction,
                                        override val messagesApi: MessagesApi,
                                        val controllerComponents: MessagesControllerComponents,
                                        fsConnector: FinancialStatementConnector,
                                        penaltiesService: PenaltiesService,
                                        schemeService: SchemeService,
                                        renderer: Renderer
                                       )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  def onPageLoad(identifier: String, startDate: LocalDate, chargeReference: String): Action[AnyContent] = identify.async {
    implicit request =>
      fsConnector.getPsaFS(request.psaId.id).flatMap {
        psaFS =>
          val filteredPsaFS = psaFS.filter(_.periodStartDate == startDate)

          val commonJson = Json.obj(
            "heading" -> heading(psaFS.filter(_.chargeReference == chargeReference).head.chargeType.toString),
            "isOverdue" -> penaltiesService.isPaymentOverdue(psaFS.filter(_.chargeReference == chargeReference).head),
            "period" -> msg"penalties.period".withArgs(startDate.format(dateFormatterStartDate),
              getQuarter(startDate).endDate.format(dateFormatterDMY)),
            "chargeReference" -> psaFS.filter(_.chargeReference == chargeReference).head.chargeReference,
            "list" -> penaltiesService.chargeDetailsRows(psaFS.filter(_.chargeReference == chargeReference).head)
          )

          if (filteredPsaFS.nonEmpty) {
            if (identifier.matches(appConfig.srnRegex)) {
              schemeService.retrieveSchemeDetails(psaId = request.psaId.id, srn = identifier).flatMap {
                schemeDetails =>
                  val json = Json.obj(
                    "schemeAssociated" -> true,
                    "schemeName" -> schemeDetails.schemeName
                  ) ++ commonJson

                  renderer.render(template = "financialStatement/chargeDetails.njk", json).map(Ok(_))
              }
            } else {
              val json = Json.obj(
                "schemeAssociated" -> false
              ) ++ commonJson

              renderer.render(template = "financialStatement/chargeDetails.njk", json).map(Ok(_))
            }
          } else {
            Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
          }
      }
  }

  val heading: String => String = s => if (s.contains('(')) s.substring(0, s.indexOf('(')) else s

}
