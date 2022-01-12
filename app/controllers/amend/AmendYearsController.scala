/*
 * Copyright 2022 HM Revenue & Customs
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
import controllers.actions._
import forms.amend.AmendYearsFormProvider
import models.requests.IdentifierRequest
import models.{AmendYears, GenericViewModel, Year}
import play.api.data.Form
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import renderer.Renderer
import services.{QuartersService, SchemeService}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class AmendYearsController @Inject()(
                                      override val messagesApi: MessagesApi,
                                      identify: IdentifierAction,
                                      formProvider: AmendYearsFormProvider,
                                      val controllerComponents: MessagesControllerComponents,
                                      renderer: Renderer,
                                      config: FrontendAppConfig,
                                      schemeService: SchemeService,
                                      quartersService: QuartersService
                                    )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  private def amendQuartersPage(srn:String, year:Int):Future[Result] =
    Future.successful(Redirect(controllers.amend.routes.AmendQuartersController.onPageLoad(srn, year.toString)))
  private def futureSessionExpiredPage:Future[Result] = Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))

  def onPageLoad(srn: String): Action[AnyContent] = identify.async { implicit request =>
    schemeService.retrieveSchemeDetails(
      psaId = request.idOrException,
      srn = srn,
      schemeIdType = "srn"
    ) flatMap { schemeDetails =>
      quartersService.getPastYears(schemeDetails.pstr).flatMap {
        case Nil => futureSessionExpiredPage
        case Seq(oneYearOnly) => amendQuartersPage(srn, oneYearOnly)
        case yearsSeq =>
          val json = Json.obj(
            "srn" -> srn,
            "startDate" -> None,
            "form" -> form(yearsSeq),
            "radios" -> AmendYears.radios(form(yearsSeq), yearsSeq),
            "viewModel" -> viewModel(schemeDetails.schemeName, srn)
          )
          renderer
            .render(template = "amend/amendYears.njk", json)
            .map(Ok(_))
      }
    }
  }


  private def form(years: Seq[Int]): Form[Year] = formProvider(years)

  private def viewModel(schemeName: String, srn: String)(implicit request: IdentifierRequest[_]): GenericViewModel = {
    GenericViewModel(
      submitUrl = routes.AmendYearsController.onSubmit(srn).url,
      returnUrl = config.schemeDashboardUrl(request).format(srn),
      schemeName = schemeName
    )
  }

  def onSubmit(srn: String): Action[AnyContent] = identify.async { implicit request =>
    schemeService.retrieveSchemeDetails(
      psaId = request.idOrException,
      srn = srn,
      schemeIdType = "srn"
    ) flatMap { schemeDetails =>
      quartersService.getPastYears(schemeDetails.pstr).flatMap {
        case Nil => futureSessionExpiredPage
        case yearsSeq =>
          form(yearsSeq).bindFromRequest().fold(
              formWithErrors => {
                val json = Json.obj(
                  fields = "srn" -> srn,
                  "startDate" -> None,
                  "form" -> formWithErrors,
                  "radios" -> AmendYears
                    .radios(formWithErrors, yearsSeq),
                  "viewModel" -> viewModel(
                    schemeDetails.schemeName,
                    srn
                  )
                )
                renderer.render(template = "amend/amendYears.njk", json).map(BadRequest(_))
              },
              value => amendQuartersPage(srn, value.year)
            )
      }
    }
  }
}
