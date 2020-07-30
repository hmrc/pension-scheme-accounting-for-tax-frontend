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

import config.FrontendAppConfig
import connectors.FinancialStatementConnector
import controllers.actions._
import javax.inject.Inject
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.{PenaltiesService, SchemeService}
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.{NunjucksSupport, Table}

import scala.concurrent.ExecutionContext

class PenaltiesController @Inject()(
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

  def onPageLoad(year: String, srn: String): Action[AnyContent] = identify.async { implicit request =>
    schemeService.retrieveSchemeDetails(request.psaId.id, srn).flatMap { schemeDetails =>
      fsConnector.getPsaFS(request.psaId.id).flatMap { psaFS =>
        val filteredPsaFS = psaFS.filter(_.pstr == schemeDetails.pstr)
        val penaltyTables: Seq[Table] = penaltiesService.getPsaFsJson(filteredPsaFS, srn, year.toInt)
        val json = Json.obj("year" -> year,
          "pstr" -> schemeDetails.pstr,
          "schemeName" -> schemeDetails.schemeName,
          "tables" -> Json.toJson(penaltyTables))
        renderer.render(template = "financialStatement/penalties.njk", json).map(Ok(_))

      }
    }
  }


}
