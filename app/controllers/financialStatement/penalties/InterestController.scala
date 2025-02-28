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

package controllers.financialStatement.penalties

import config.Constants._
import config.FrontendAppConfig
import controllers.actions.{AllowAccessActionProviderForIdentifierRequest, IdentifierAction}
import models.PenaltiesFilter
import models.financialStatement.PsaFSDetail
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.{PenaltiesService, SchemeService}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.DateHelper.{dateFormatterDMY, dateFormatterStartDate}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import views.html.financialStatement.penalties.InterestView

class InterestController @Inject()(
                                         identify: IdentifierAction,
                                         allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                         override val messagesApi: MessagesApi,
                                         val controllerComponents: MessagesControllerComponents,
                                         penaltiesService: PenaltiesService,
                                         schemeService: SchemeService,
                                         interestView: InterestView,
                                         config: FrontendAppConfig
                                       )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport {

  def onPageLoad(identifier: String, chargeReferenceIndex: String): Action[AnyContent] = (identify andThen allowAccess()).async {
    implicit request =>
      penaltiesService.getPenaltiesFromCache(request.psaIdOrException.id).flatMap { penaltiesCache =>

          val chargeRefs: Seq[String] = penaltiesCache.penalties.map(_.chargeReference)
          def penaltyOpt: Option[PsaFSDetail] = penaltiesCache.penalties.find(_.chargeReference == chargeRefs(chargeReferenceIndex.toInt))

          if(chargeRefs.length > chargeReferenceIndex.toInt && penaltyOpt.nonEmpty) {
            val fs = penaltyOpt.head
            val psaFS = penaltiesCache.penalties
            val chargeTypeDescription = psaFS.filter(_.chargeReference == chargeRefs(chargeReferenceIndex.toInt)).head.chargeType.toString.toLowerCase

            if (identifier.matches(srnRegex)) {
                  schemeService.retrieveSchemeDetails(request.idOrException, identifier) flatMap {
                    schemeDetails =>
                      Future.successful(Ok(interestView(
                        heading(Messages("penalties.column.chargeType.interestOn", chargeTypeDescription)),
                        schemeAssociated = true,
                        Some(schemeDetails.schemeName),
                        period = Messages("penalties.period", fs.periodStartDate.format(dateFormatterStartDate), fs.periodEndDate.format(dateFormatterDMY)),
                        chargeReference = Messages("penalties.column.chargeReference.toBeAssigned"),
                        list = penaltiesService.interestRows(psaFS.filter(_.chargeReference == chargeRefs(chargeReferenceIndex.toInt)).head),
                        originalAmountURL = controllers.financialStatement.penalties.routes.ChargeDetailsController
                          .onPageLoad(identifier, chargeReferenceIndex, PenaltiesFilter.All).url,
                        returnUrl = config.managePensionsSchemeOverviewUrl,
                        psaName = penaltiesCache.psaName
                      )))
                  }
                } else {
                  Future.successful(Ok(interestView(
                    heading(Messages("penalties.column.chargeType.interestOn", chargeTypeDescription)),
                    schemeAssociated = false,
                    None,
                    period = Messages("penalties.period", fs.periodStartDate.format(dateFormatterStartDate), fs.periodEndDate.format(dateFormatterDMY)),
                    chargeReference = Messages("penalties.column.chargeReference.toBeAssigned"),
                    list = penaltiesService.interestRows(psaFS.filter(_.chargeReference == chargeRefs(chargeReferenceIndex.toInt)).head),
                    originalAmountURL = controllers.financialStatement.penalties.routes.ChargeDetailsController
                      .onPageLoad(identifier, chargeReferenceIndex, PenaltiesFilter.All).url,
                    returnUrl = config.managePensionsSchemeOverviewUrl,
                    psaName = penaltiesCache.psaName
                  )))
                }

        } else {
          Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
        }

      }

  }

  private val heading: String => String = s => if (s.contains('(')) s.substring(0, s.indexOf('(')) else s
}
