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

import config.FrontendAppConfig
import controllers.actions._
import forms.YearsFormProvider
import models.financialStatement.PsaFS
import models.{DisplayYear, FSYears, PaymentOverdue, Year}
import play.api.data.Form
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.PenaltiesService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class SelectPenaltiesYearController @Inject()(override val messagesApi: MessagesApi,
                                              identify: IdentifierAction,
                                              allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                              formProvider: YearsFormProvider,
                                              val controllerComponents: MessagesControllerComponents,
                                              renderer: Renderer,
                                              config: FrontendAppConfig,
                                              service: PenaltiesService)
                                             (implicit ec: ExecutionContext) extends FrontendBaseController
                                                                      with I18nSupport
                                                                      with NunjucksSupport {

  private def form(implicit config: FrontendAppConfig): Form[Year] = formProvider("selectPenaltiesYear.error")

  def onPageLoad: Action[AnyContent] = (identify andThen allowAccess()).async { implicit request =>
    service.getPenaltiesFromCache(request.psaIdOrException.id).flatMap { penalties =>
      val years = getYears(penalties)
      val json = Json.obj(
        "form" -> form(config),
        "radios" -> FSYears.radios(form(config), years),
        "submitUrl" -> routes.SelectPenaltiesYearController.onSubmit().url
      )

      renderer.render(template = "financialStatement/penalties/selectYear.njk", json).map(Ok(_))
    }
  }

  def onSubmit: Action[AnyContent] = identify.async { implicit request =>
    service.getPenaltiesFromCache(request.psaIdOrException.id).flatMap { penalties =>
      form(config).bindFromRequest().fold(
        formWithErrors => {
          val json = Json.obj(
            "form" -> formWithErrors,
            "radios" -> FSYears.radios(formWithErrors, getYears(penalties)),
            "submitUrl" -> routes.SelectPenaltiesYearController.onSubmit().url
          )
          renderer.render(template = "financialStatement/penalties/selectYear.njk", json).map(BadRequest(_))
        },
      value => {
        val quartersSeq = penalties.filter(_.periodStartDate.getYear == value.year).map(_.periodStartDate).distinct
        if (quartersSeq.size == 1) {
          Future.successful(Redirect(routes.SelectSchemeController.onPageLoad(quartersSeq.head.toString)))
        } else {
          Future.successful(Redirect(routes.SelectPenaltiesQuarterController.onPageLoad(value.getYear.toString)))
        }
      }
      )
    }
  }

  private def getYears(penalties: Seq[PsaFS]): Seq[DisplayYear] = penalties.map(_.periodStartDate.getYear).distinct.sorted.reverse.map { year =>
    val hint = if (penalties.filter(_.periodStartDate.getYear == year).exists(service.isPaymentOverdue)) Some(PaymentOverdue) else None
    DisplayYear(year, hint)
  }

}
