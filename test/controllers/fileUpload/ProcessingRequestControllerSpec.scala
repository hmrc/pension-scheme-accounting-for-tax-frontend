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

package controllers.racdac.bulk

import connectors.cache.{BulkMigrationEventsLogConnector, BulkMigrationQueueConnector}
import controllers.ControllerSpecBase
import controllers.actions.MutableFakeDataRetrievalAction
import matchers.JsonMatchers
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers._
import play.twirl.api.Html
import uk.gov.hmrc.nunjucks.NunjucksSupport
import utils.Enumerable

import scala.concurrent.Future

class ProcessingRequestControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers with Enumerable.Implicits {

  private val templateToBeRendered = "racdac/processingRequest.njk"
  private val mockQueueConnector = mock[BulkMigrationQueueConnector]
  private val mockBulkMigrationEventsLogConnector = mock[BulkMigrationEventsLogConnector]
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val extraModules: Seq[GuiceableModule] = Seq(
    bind[BulkMigrationQueueConnector].to(mockQueueConnector),
    bind[BulkMigrationEventsLogConnector].to(mockBulkMigrationEventsLogConnector)
  )
  private val application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, extraModules).build()

  private def httpPathGET: String = controllers.racdac.bulk.routes.ProcessingRequestController.onPageLoad().url

  private def jsonToPassToTemplate(heading: String, content: String, redirect: String): JsObject =
    Json.obj(
      "pageTitle" -> heading,
      "heading" -> heading,
      "content" -> content,
      "continueUrl" -> redirect
    )

  override def beforeEach: Unit = {
    super.beforeEach
    reset(mockBulkMigrationEventsLogConnector)
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
  }

  "ProcessingRequestController" must {

    "return OK and the correct view for a GET when migration events log is ACCEPTED" in {
      when(mockBulkMigrationEventsLogConnector.getStatus(any(), any())).thenReturn(Future.successful(ACCEPTED))
      val templateCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor: ArgumentCaptor[JsObject] = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered

      jsonCaptor.getValue must containJson(jsonToPassToTemplate(
        heading = "messages__processingRequest__h1_processed",
        content = "messages__processingRequest__content_processed",
        redirect = routes.ConfirmationController.onPageLoad().url
      ))
    }

    "return OK and the correct view for a GET when migration events log is NOT_FOUND" in {
      when(mockBulkMigrationEventsLogConnector.getStatus(any(), any())).thenReturn(Future.successful(NOT_FOUND))
      val templateCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor: ArgumentCaptor[JsObject] = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered

      jsonCaptor.getValue must containJson(jsonToPassToTemplate(
        heading = "messages__processingRequest__h1_processing",
        content = "messages__processingRequest__content_processing",
        redirect = routes.ProcessingRequestController.onPageLoad().url
      ))
    }

    "return OK and the correct view for a GET when migration events log is INTERNAL_SERVER_ERROR" in {
      when(mockBulkMigrationEventsLogConnector.getStatus(any(), any())).thenReturn(Future.successful(INTERNAL_SERVER_ERROR))
      val templateCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor: ArgumentCaptor[JsObject] = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered

      jsonCaptor.getValue must containJson(jsonToPassToTemplate(
        heading = "messages__processingRequest__h1_failure",
        content = "messages__processingRequest__content_failure",
        redirect = routes.DeclarationController.onPageLoad().url
      ))
    }
  }
}
