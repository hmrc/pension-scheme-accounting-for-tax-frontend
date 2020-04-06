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

package controllers.amend

import config.FrontendAppConfig
import connectors.AFTConnector
import controllers.actions._
import forms.amend.AmendQuartersFormProvider
import javax.inject.Inject
import models.LocalDateBinder._
import models.{AmendQuarters, GenericViewModel, Quarters}
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.SchemeService
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.{ExecutionContext, Future}

class AmendQuartersController @Inject()(
                                         override val messagesApi: MessagesApi,
                                         identify: IdentifierAction,
                                         formProvider: AmendQuartersFormProvider,
                                         val controllerComponents: MessagesControllerComponents,
                                         renderer: Renderer,
                                         config: FrontendAppConfig,
                                         schemeService: SchemeService,
                                         aftConnector: AFTConnector
                                    )(implicit ec: ExecutionContext) extends FrontendBaseController with I18nSupport with NunjucksSupport {

  private def form(quarters: Seq[Quarters])(implicit messages: Messages): Form[Quarters] =
    formProvider(messages("amendQuarters.error.required"), quarters)

  def onPageLoad(srn: String, year: String): Action[AnyContent] = identify.async {
    implicit request =>

      schemeService.retrieveSchemeDetails(request.psaId.id, srn).flatMap { schemeDetails =>
        aftConnector.getAftOverview(schemeDetails.pstr).flatMap { aftOverview =>
          if (aftOverview.nonEmpty) {

            val quarters = aftOverview.filter(_.periodStartDate.getYear == year.toInt).map {overviewElement =>
              AmendQuarters.getQuartersFromDate(overviewElement.periodStartDate)
            }

            val json = Json.obj(
              "srn" -> srn,
              "startDate" -> None,
              "form" -> form(quarters),
              "radios" -> AmendQuarters.radios(form(quarters), quarters),
              "viewModel" -> viewModel(srn, year, schemeDetails.schemeName),
              "year" -> year
            )

            renderer.render(template = "amend/amendQuarters.njk", json).map(Ok(_))
          } else {
            Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
          }
        }
      }
  }

  def onSubmit(srn: String, year: String): Action[AnyContent] = identify.async {
    implicit request =>
      schemeService.retrieveSchemeDetails(request.psaId.id, srn).flatMap { schemeDetails =>
        aftConnector.getAftOverview(schemeDetails.pstr).flatMap { aftOverview =>
          if (aftOverview.nonEmpty) {

            val quarters = aftOverview.filter(_.periodStartDate.getYear == year.toInt).map {overviewElement =>
              AmendQuarters.getQuartersFromDate(overviewElement.periodStartDate)
            }

            form(quarters).bindFromRequest().fold(
              formWithErrors => {
                schemeService.retrieveSchemeDetails(request.psaId.id, srn).flatMap { schemeDetails =>
                  val json = Json.obj(
                    fields = "srn" -> srn,
                    "startDate" -> None,
                    "form" -> formWithErrors,
                    "radios" -> AmendQuarters.radios(formWithErrors, quarters),
                    "viewModel" -> viewModel(srn, year, schemeDetails.schemeName),
                    "year" -> year
                  )
                  renderer.render(template = "amend/amendQuarters.njk", json).map(BadRequest(_))
                }
              },
              value => {
                val aftOverviewElement = aftOverview.filter(_.periodStartDate == AmendQuarters.getStartDate(value, year.toInt))
                if(aftOverviewElement.nonEmpty && !aftOverviewElement.head.submittedVersionAvailable) {
                  Future.successful(Redirect(controllers.routes.AFTSummaryController.onPageLoad(srn, AmendQuarters.getStartDate(value, year.toInt), Some("1"))))
                } else {
                  Future.successful(Redirect(controllers.amend.routes.ReturnHistoryController.onPageLoad(srn, AmendQuarters.getStartDate(value, year.toInt))))
                }
              }
            )
          } else {
            Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
          }
        }
      }
  }

  private def viewModel(srn: String, year: String, schemeName: String): GenericViewModel =
      GenericViewModel(
        submitUrl = routes.AmendQuartersController.onSubmit(srn, year).url,
        returnUrl = config.managePensionsSchemeSummaryUrl.format(srn),
        schemeName = schemeName
      )
}
