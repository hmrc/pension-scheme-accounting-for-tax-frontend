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

package controllers.amend

import config.FrontendAppConfig
import controllers.actions._
import forms.amend.AmendYearsFormProvider
import models.{AmendYears, Year}
import play.api.data.Form
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import services.{QuartersService, SchemeService}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import views.html.amend.AmendYearsView

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class AmendYearsController @Inject()(
                                      override val messagesApi: MessagesApi,
                                      identify: IdentifierAction,
                                      formProvider: AmendYearsFormProvider,
                                      val controllerComponents: MessagesControllerComponents,
                                      config: FrontendAppConfig,
                                      schemeService: SchemeService,
                                      quartersService: QuartersService,
                                      allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                      amendYearsView: AmendYearsView
                                    )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport {

  private def amendQuartersPage(srn:String, year:Int):Future[Result] =
    Future.successful(Redirect(controllers.amend.routes.AmendQuartersController.onPageLoad(srn, year.toString)))
  private def futureSessionExpiredPage:Future[Result] = Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))

  def onPageLoad(srn: String): Action[AnyContent] = (identify andThen allowAccess(Some(srn))).async { implicit request =>
    schemeService.retrieveSchemeDetails(
      psaId = request.idOrException,
      srn = srn
    ) flatMap { schemeDetails =>
      quartersService.getPastYears(schemeDetails.pstr, srn, request.isLoggedInAsPsa).flatMap {
        case Nil => futureSessionExpiredPage
        case Seq(oneYearOnly) => amendQuartersPage(srn, oneYearOnly)
        case yearsSeq =>
          Future.successful(Ok(amendYearsView(
            form(yearsSeq),
            AmendYears.radios(form(yearsSeq), yearsSeq),
            routes.AmendYearsController.onSubmit(srn),
            config.schemeDashboardUrl(request).format(srn),
            schemeDetails.schemeName
          )))
      }
    }
  }


  private def form(years: Seq[Int]): Form[Year] = formProvider(years)

  def onSubmit(srn: String): Action[AnyContent] = (identify andThen allowAccess(Some(srn))).async { implicit request =>
    schemeService.retrieveSchemeDetails(
      psaId = request.idOrException,
      srn = srn
    ) flatMap { schemeDetails =>
      quartersService.getPastYears(schemeDetails.pstr, srn, request.isLoggedInAsPsa).flatMap {
        case Nil => futureSessionExpiredPage
        case yearsSeq =>
          form(yearsSeq).bindFromRequest().fold(
              formWithErrors => {
                Future.successful(BadRequest(amendYearsView(
                  formWithErrors,
                  AmendYears.radios(formWithErrors, yearsSeq),
                  routes.AmendYearsController.onSubmit(srn),
                  config.schemeDashboardUrl(request).format(srn),
                  schemeDetails.schemeName
                )))
              },
              value => amendQuartersPage(srn, value.year)
            )
      }
    }
  }
}
