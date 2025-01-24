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

package handlers

import config.FrontendAppConfig
import play.api.http.HeaderNames.CACHE_CONTROL
import play.api.http.HttpErrorHandler
import play.api.http.Status._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.Results._
import play.api.mvc.{Request, RequestHeader, Result, Results}
import play.api.{Logger, PlayException}
import views.html.{ErrorView, NotFoundView}

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

// NOTE: There should be changes to bootstrap to make this easier, the API in bootstrap should allow a `Future[Html]` rather than just an `Html`
@Singleton
class ErrorHandler @Inject()(
                              errorView: ErrorView,
                              notFoundView: NotFoundView,
                              val messagesApi: MessagesApi,
                              config: FrontendAppConfig
                            )
  extends HttpErrorHandler
    with I18nSupport {

  private val logger = Logger(classOf[ErrorHandler])

  override def onClientError(request: RequestHeader, statusCode: Int, message: String = ""): Future[Result] = {

    implicit def requestImplicit: Request[_] = Request(request, "")

    logger.warn(s"Errorhandler onClientError:statusCode = $statusCode and message = $message")
    statusCode match {
      case BAD_REQUEST =>
        Future.successful(BadRequest(errorView(
          "global.error.badRequest400", "global.error.badRequest400.message")))
      case NOT_FOUND =>
        Future.successful(NotFound(notFoundView(config.yourPensionSchemesUrl)))
      case _ =>
        Future.successful(Results.Status(statusCode)(errorView(
          "Error", message)))
    }
  }

  override def onServerError(request: RequestHeader, exception: Throwable): Future[Result] = {

    implicit def requestImplicit: Request[_] = Request(request, "")

    logError(request, exception)
    exception match {
      case ApplicationException(result, _) =>
        Future.successful(result)
      case _ =>
        Future.successful(InternalServerError(errorView(
          "global.error.InternalServerError500", "global.error.InternalServerError500.message"
        )).withHeaders(CACHE_CONTROL -> "no-cache"))
    }
  }

  private def logError(request: RequestHeader, ex: Throwable): Unit =
    logger.error(
      """
        |
        |! %sInternal server error, for (%s) [%s] ->
        | """.stripMargin.format(ex match {
        case p: PlayException => "@" + p.id + " - "
        case _ => ""
      }, request.method, request.uri),
      ex
    )
}

case class ApplicationException(result: Result, message: String) extends Exception(message)
