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
import forms.amend.AmendYearsFormProvider
import javax.inject.Inject
import models.amend.AmendYears
import models.{GenericViewModel, Years}
import play.api.data.Form
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.SchemeService
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.{ExecutionContext, Future}

class AmendYearsController @Inject()(
                                 override val messagesApi: MessagesApi,
                                 identify: IdentifierAction,
                                 formProvider: AmendYearsFormProvider,
                                 val controllerComponents: MessagesControllerComponents,
                                 renderer: Renderer,
                                 config: FrontendAppConfig,
                                 aftConnector: AFTConnector,
                                 schemeService: SchemeService
                                    )(implicit ec: ExecutionContext) extends FrontendBaseController with I18nSupport with NunjucksSupport {

  private def form(years: Seq[Int]): Form[AmendYears] = formProvider(years)

  def onPageLoad(srn: String): Action[AnyContent] = identify.async {
    implicit request =>

      schemeService.retrieveSchemeDetails(request.psaId.id, srn).flatMap { schemeDetails =>
        aftConnector.getAftOverview(schemeDetails.pstr).flatMap { aftOverview =>
          if (aftOverview.nonEmpty) {
            val yearsSeq = aftOverview.map(_.periodStartDate.getYear).distinct

            val json = Json.obj(
              "srn" -> srn,
              "startDate" -> None,
              "form" -> form(yearsSeq),
              "radios" -> AmendYears.radios(form(yearsSeq), yearsSeq),
              "viewModel" -> viewModel(schemeDetails.schemeName, srn)
            )

            renderer.render(template = "years.njk", json).map(Ok(_))
          } else {
            Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
          }
        }
      }
  }

  def onSubmit(srn: String): Action[AnyContent] = identify.async {
    implicit request =>
      schemeService.retrieveSchemeDetails(request.psaId.id, srn).flatMap { schemeDetails =>
        aftConnector.getAftOverview(schemeDetails.pstr).flatMap { aftOverview =>
          if (aftOverview.nonEmpty) {
            val yearsSeq = aftOverview.map(_.periodStartDate.getYear).distinct
            form(yearsSeq).bindFromRequest().fold(
              formWithErrors =>
                schemeService.retrieveSchemeDetails(request.psaId.id, srn).flatMap { schemeDetails =>
                  val json = Json.obj(
                    fields = "srn" -> srn,
                    "startDate" -> None,
                    "form" -> formWithErrors,
                    "radios" -> Years.radios(formWithErrors)(implicitly, config),
                    "viewModel" -> viewModel(schemeDetails.schemeName, srn)
                  )
                  renderer.render(template = "years.njk", json).map(BadRequest(_))
                },
              value =>
                Future.successful(Redirect(controllers.routes.QuartersController.onPageLoad(srn, value.toString)))
            )
          } else {
            Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
          }
        }
      }
  }

  private def viewModel(schemeName: String, srn: String): GenericViewModel = {
    GenericViewModel(
      submitUrl = routes.AmendYearsController.onSubmit(srn).url,
      returnUrl = config.managePensionsSchemeSummaryUrl.format(srn),
      schemeName = schemeName
    )
  }
}
