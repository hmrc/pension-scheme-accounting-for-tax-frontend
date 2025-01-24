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

package controllers.chargeD

import controllers.actions._
import models.ChargeType.ChargeTypeLifetimeAllowance
import models.LocalDateBinder._
import models.{AccessType, NormalMode}
import navigators.CompoundNavigator
import pages.chargeD.WhatYouWillNeedPage
import pages.{IsPublicServicePensionsRemedyPage, SchemeNameQuery}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import views.html.chargeD.WhatYouWillNeedView

import java.time.LocalDate
import javax.inject.Inject

class WhatYouWillNeedController @Inject()(
    override val messagesApi: MessagesApi,
    identify: IdentifierAction,
    getData: DataRetrievalAction,
    allowAccess: AllowAccessActionProvider,
    requireData: DataRequiredAction,
    val controllerComponents: MessagesControllerComponents,
    navigator: CompoundNavigator,
    view: WhatYouWillNeedView
) extends FrontendBaseController with I18nSupport {

  def onPageLoad(srn: String, startDate: LocalDate, accessType: AccessType, version: Int, index: Int): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate, None, version, accessType)) {
      implicit request =>
        val ua = request.userAnswers

        val psr: Option[String] = ua.get(IsPublicServicePensionsRemedyPage(ChargeTypeLifetimeAllowance, Some(index))) match {
          case Some(true) => Some("chargeD.whatYouWillNeed.li6")
          case _ => None
        }

        val returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url
        val nextPage = navigator.nextPage(WhatYouWillNeedPage, NormalMode, ua, srn, startDate, accessType, version).url
        val schemeName = ua.get(SchemeNameQuery).getOrElse("the scheme")

        Ok(view(nextPage, schemeName, returnUrl, psr))
    }
}
