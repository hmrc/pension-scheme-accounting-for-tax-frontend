/*
 * Copyright 2023 HM Revenue & Customs
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
import controllers.actions.{AllowAccessActionProviderForIdentifierRequest, IdentifierAction}
import models.PenaltiesFilter
import models.financialStatement.PsaFSDetail
import models.requests.IdentifierRequest
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.{PenaltiesService, SchemeService}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport
import utils.DateHelper.{dateFormatterDMY, dateFormatterStartDate}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ChargeDetailsController @Inject()(
                                         identify: IdentifierAction,
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

  def onPageLoad(identifier: String, chargeReferenceIndex: String, journeyType: PenaltiesFilter): Action[AnyContent] = (identify andThen allowAccess()).async {
    implicit request =>
      penaltiesService.getPenaltiesForJourney(request.psaIdOrException.id, journeyType).flatMap { penaltiesCache =>
          val chargeRefs: Seq[String] = penaltiesCache.penalties.map(_.chargeReference)
          def penaltyOpt: Option[PsaFSDetail] = penaltiesCache.penalties.find(_.chargeReference == chargeRefs(chargeReferenceIndex.toInt))

          if(chargeRefs.length > chargeReferenceIndex.toInt && penaltyOpt.nonEmpty) {
                if (identifier.matches(srnRegex)) {
                  schemeService.retrieveSchemeDetails(request.idOrException, identifier, "srn") flatMap {
                    schemeDetails =>
                      val json = Json.obj(
                        "psaName" -> penaltiesCache.psaName,
                        "schemeAssociated" -> true,
                        "schemeName" -> schemeDetails.schemeName
                      ) ++ commonJson(penaltyOpt.head, penaltiesCache.penalties, chargeRefs, chargeReferenceIndex)

                      renderer.render(template = "financialStatement/penalties/chargeDetails.njk", json).map(Ok(_))
                  }
                } else {
                  val json = Json.obj(
                    "psaName" -> penaltiesCache.psaName,
                    "schemeAssociated" -> false
                  ) ++ commonJson(penaltyOpt.head, penaltiesCache.penalties, chargeRefs, chargeReferenceIndex)

                  renderer.render(template = "financialStatement/penalties/chargeDetails.njk", json).map(Ok(_))
                }

        } else {
          Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
        }

      }

  }

  private def commonJson(
                          fs: PsaFSDetail,
                          psaFS: Seq[PsaFSDetail],
                          chargeRefs: Seq[String],
                          chargeReferenceIndex: String
                        )(implicit request: IdentifierRequest[AnyContent]): JsObject =
    Json.obj(
      "heading" ->   heading(psaFS.filter(_.chargeReference == chargeRefs(chargeReferenceIndex.toInt)).head.chargeType.toString),
      "isOverdue" ->        penaltiesService.isPaymentOverdue(psaFS.filter(_.chargeReference == chargeRefs(chargeReferenceIndex.toInt)).head),
      "period" ->           Messages("penalties.period", fs.periodStartDate.format(dateFormatterStartDate), fs.periodEndDate.format(dateFormatterDMY)),
      "chargeReference" ->  fs.chargeReference,
      "list" ->             penaltiesService.chargeDetailsRows(psaFS.filter(_.chargeReference == chargeRefs(chargeReferenceIndex.toInt)).head)
    )

  private val heading: String => String = s => if (s.contains('(')) s.substring(0, s.indexOf('(')) else s
}
