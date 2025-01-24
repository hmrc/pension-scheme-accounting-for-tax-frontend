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

package controllers

import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import controllers.actions._
import forms.YesNoFormProvider
import models.ChargeType.ChargeTypeLifetimeAllowance
import models.LocalDateBinder._
import models.{AccessType, ChargeType, Index, Mode, NormalMode, UserAnswers}
import navigators.CompoundNavigator
import pages.{IsPublicServicePensionsRemedyPage, MemberFormCompleted, QuarterPage}
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.UserAnswersService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import viewmodels.TwirlRadios

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success
import views.html.IsPublicServicePensionsRemedyView

class IsPublicServicePensionsRemedyController @Inject()(override val messagesApi: MessagesApi,
                                                        userAnswersCacheConnector: UserAnswersCacheConnector,
                                                        userAnswersService: UserAnswersService,
                                                        navigator: CompoundNavigator,
                                                        identify: IdentifierAction,
                                                        getData: DataRetrievalAction,
                                                        allowAccess: AllowAccessActionProvider,
                                                        requireData: DataRequiredAction,
                                                        config: FrontendAppConfig,
                                                        formProvider: YesNoFormProvider,
                                                        val controllerComponents: MessagesControllerComponents,
                                                        isPublicServicePensionsRemedyView: IsPublicServicePensionsRemedyView)(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport {

  private def form(memberName: String, optIndex: Option[Index])(implicit messages: Messages): Form[Boolean] = {
    optIndex match {
      case Some(_) => formProvider(messages("isPublicServicePensionsRemedy.error.required", memberName))
      case None => formProvider(messages("isPublicServicePensionsRemedyBulk.error.required", memberName))
    }
  }

  def onPageLoad(chargeType: ChargeType,
                 mode: Mode,
                 srn: String,
                 startDate: LocalDate,
                 accessType: AccessType,
                 version: Int,
                 index: Option[Index]): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate, None, version, accessType)).async {
      implicit request =>
        DataRetrievals.retrieveSchemeName { schemeName =>
          if (isPsrAlwaysTrueForLtaAbolition(chargeType, request.userAnswers)) {
            for {
              updatedAnswers <- Future.fromTry(userAnswersService.set(IsPublicServicePensionsRemedyPage(chargeType, index), true, mode))
              _ <- userAnswersCacheConnector
                .savePartial(request.internalId, updatedAnswers.data, chargeType = Some(chargeType), memberNo = index.map(_.id))
            } yield {
              Redirect(navigator.nextPage(IsPublicServicePensionsRemedyPage(chargeType, index), mode, updatedAnswers, srn, startDate, accessType, version))
            }
          } else {
            val chargeTypeDescription = Messages(s"chargeType.description.${chargeType.toString}")

            val (heading, title) = index match {
              case Some(_) => Tuple2("isPublicServicePensionsRemedy.heading", "isPublicServicePensionsRemedy.title")
              case None => Tuple2("isPublicServicePensionsRemedyBulk.heading", "isPublicServicePensionsRemedyBulk.title")
            }

            val preparedForm = request.userAnswers.get(IsPublicServicePensionsRemedyPage(chargeType, index)) match {
              case None => form(chargeTypeDescription, index)
              case Some(value) => form(chargeTypeDescription, index).fill(value)
            }

            Future.successful(Ok(isPublicServicePensionsRemedyView(
              title,
              heading,
              chargeTypeDescription,
              preparedForm,
              TwirlRadios.yesNo(preparedForm("value")),
              routes.IsPublicServicePensionsRemedyController.onSubmit(chargeType, mode, srn, startDate, accessType, version, index),
              controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
              schemeName
            )))
          }
        }
    }

  // scalastyle:off method.length
  def onSubmit(chargeType: ChargeType,
               mode: Mode,
               srn: String,
               startDate: LocalDate,
               accessType: AccessType,
               version: Int,
               index: Option[Index]): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate, None, version, accessType)).async { implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>
        val chargeTypeDescription = Messages(s"chargeType.description.${chargeType.toString}")
        form(chargeTypeDescription, index)
          .bindFromRequest()
          .fold(formWithErrors => {

            val (heading, title) = index match {
              case Some(_) => Tuple2("isPublicServicePensionsRemedy.heading", "isPublicServicePensionsRemedy.title")
              case None => Tuple2("isPublicServicePensionsRemedyBulk.heading", "isPublicServicePensionsRemedyBulk.title")
            }

            Future.successful(BadRequest(isPublicServicePensionsRemedyView(
              title,
              heading,
              chargeTypeDescription,
              formWithErrors,
              TwirlRadios.yesNo(formWithErrors("value")),
              routes.IsPublicServicePensionsRemedyController.onSubmit(chargeType, mode, srn, startDate, accessType, version, index),
              controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
              schemeName
            )))
          },
            value => {
              for {
                updatedAnswersInProgress <- Future.fromTry(userAnswersService.set(IsPublicServicePensionsRemedyPage(chargeType, index), value, mode))
                updatedAnswers <- Future.fromTry(mode match{
                  case NormalMode => updatedAnswersInProgress.set(
                    MemberFormCompleted(ChargeType.chargeTypeNode(chargeType), index.getOrElse(Index(0)).toInt), false)
                  case _ => Success(updatedAnswersInProgress)
                })
                _ <- userAnswersCacheConnector
                  .savePartial(request.internalId, updatedAnswers.data, chargeType = Some(chargeType), memberNo = index.map(_.id))
              } yield {
                Redirect(navigator.nextPage(IsPublicServicePensionsRemedyPage(chargeType, index), mode, updatedAnswers, srn, startDate, accessType, version))
              }
            }
          )
      }
    }

  private def isPsrAlwaysTrueForLtaAbolition(chargeType: ChargeType, ua: UserAnswers): Boolean = {
    ua.get(QuarterPage) match {
      case Some(aftQuarter) if chargeType == ChargeTypeLifetimeAllowance =>
        val mccloudPsrAlwaysTrueStartDate = config.mccloudPsrAlwaysTrueStartDate
        aftQuarter.startDate.isAfter(mccloudPsrAlwaysTrueStartDate) || aftQuarter.startDate.isEqual(mccloudPsrAlwaysTrueStartDate)
      case _ => false
    }
  }
}
