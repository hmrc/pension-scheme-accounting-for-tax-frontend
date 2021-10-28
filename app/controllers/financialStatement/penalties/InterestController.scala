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
import controllers.actions.{IdentifierAction, AllowAccessActionProviderForIdentifierRequest}
import models.PenaltiesFilter
import models.financialStatement.PsaFS
import models.requests.IdentifierRequest
import play.api.i18n.{MessagesApi, Messages, I18nSupport}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.{PenaltiesService, SchemeService}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport
import utils.DateHelper.{dateFormatterStartDate, dateFormatterDMY}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class InterestController @Inject()(
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

  def onPageLoad(identifier: String, chargeReferenceIndex: String): Action[AnyContent] = (identify andThen allowAccess()).async {
    implicit request =>
      penaltiesService.getPenaltiesFromCache(request.psaIdOrException.id).flatMap { penaltiesCache =>

          val chargeRefs: Seq[String] = penaltiesCache.penalties.map(_.chargeReference)
          def penaltyOpt: Option[PsaFS] = penaltiesCache.penalties.find(_.chargeReference == chargeRefs(chargeReferenceIndex.toInt))

          if(chargeRefs.length > chargeReferenceIndex.toInt && penaltyOpt.nonEmpty) {
                if (identifier.matches(srnRegex)) {
                  schemeService.retrieveSchemeDetails(request.idOrException, identifier, "srn") flatMap {
                    schemeDetails =>
                      val json = Json.obj(
                        "psaName" -> penaltiesCache.psaName,
                        "schemeAssociated" -> true,
                        "schemeName" -> schemeDetails.schemeName
                      ) ++ commonJson(penaltyOpt.head, penaltiesCache.penalties, chargeRefs, chargeReferenceIndex, identifier)

                      renderer.render(template = "financialStatement/penalties/interest.njk", json).map(Ok(_))
                  }
                } else {
                  val json = Json.obj(
                    "psaName" -> penaltiesCache.psaName,
                    "schemeAssociated" -> false
                  ) ++ commonJson(penaltyOpt.head, penaltiesCache.penalties, chargeRefs, chargeReferenceIndex, identifier)

                  renderer.render(template = "financialStatement/penalties/interest.njk", json).map(Ok(_))
                }

        } else {
          Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
        }

      }

  }

  private def commonJson(
                          fs: PsaFS,
                          psaFS: Seq[PsaFS],
                          chargeRefs: Seq[String],
                          chargeReferenceIndex: String,
                          identifier: String
                        )(implicit request: IdentifierRequest[AnyContent]): JsObject = {
    val chargeTypeDescription = psaFS.filter(_.chargeReference == chargeRefs(chargeReferenceIndex.toInt)).head.chargeType.toString.toLowerCase
    Json.obj(
      "heading" ->   heading(Messages("penalties.column.chargeType.interestOn", chargeTypeDescription)),
      "period" ->           Messages("penalties.period", fs.periodStartDate.format(dateFormatterStartDate), fs.periodEndDate.format(dateFormatterDMY)),
      "chargeReference" ->  Messages("penalties.column.chargeReference.toBeAssigned"),
      "list" ->             penaltiesService.interestRows(psaFS.filter(_.chargeReference == chargeRefs(chargeReferenceIndex.toInt)).head),
      "originalAmountURL" -> controllers.financialStatement.penalties.routes.ChargeDetailsController
        .onPageLoad(identifier, chargeReferenceIndex, PenaltiesFilter.All).url
    )
  }

  private val heading: String => String = s => if (s.contains('(')) s.substring(0, s.indexOf('(')) else s
}
