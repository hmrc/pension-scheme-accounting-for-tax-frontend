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

package renderer

import org.mockito.Matchers.any
import org.mockito.Mockito.{times, verify, when}
import org.mockito.{ArgumentCaptor, Mockito}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterEach, FreeSpec, MustMatchers}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json._
import play.api.test.FakeRequest
import play.twirl.api.Html
import uk.gov.hmrc.nunjucks.NunjucksRenderer

import scala.concurrent.Future

class RendererSpec extends FreeSpec with MustMatchers with GuiceOneAppPerSuite with MockitoSugar
  with ScalaFutures with BeforeAndAfterEach {

  val mockNunjucksRenderer: NunjucksRenderer = mock[NunjucksRenderer]

  override def beforeEach(): Unit = {
    Mockito.reset(mockNunjucksRenderer)
  }

  private val applicationBuilder =
    new GuiceApplicationBuilder()
      .overrides(
        bind[NunjucksRenderer].toInstance(mockNunjucksRenderer)
      )

  implicit private val request: FakeRequest[_] = FakeRequest()

  "render" - {

    "must pass config values to the Nunjucks Renderer" - {

      "when called with only a template" in {

        when(mockNunjucksRenderer.render(any(), any())(any()))
          .thenReturn(Future.successful(Html("")))

        val templateCaptor = ArgumentCaptor.forClass(classOf[String])
        val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

        val application = applicationBuilder.build()

        val renderer = application.injector.instanceOf[Renderer]

        renderer.render("foo").futureValue

        verify(mockNunjucksRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

        val json = jsonCaptor.getValue

        (json \ "config") mustBe a[JsDefined]

        application.stop()
      }
    }

    "must pass config values to the Nunjucks Renderer" - {

      "when called with a template and a JsObject" in {

        when(mockNunjucksRenderer.render(any(), any())(any()))
          .thenReturn(Future.successful(Html("")))

        val templateCaptor = ArgumentCaptor.forClass(classOf[String])
        val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

        val application = applicationBuilder.build()

        val renderer = application.injector.instanceOf[Renderer]

        renderer.render("foo", Json.obj("bar" -> "baz")).futureValue

        verify(mockNunjucksRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

        val json = jsonCaptor.getValue

        (json \ "config") mustBe a[JsDefined]

        application.stop()
      }
    }

    "must pass config values to the Nunjucks Renderer" - {

      "when called with a template and a writable object" in {

        when(mockNunjucksRenderer.render(any(), any())(any()))
          .thenReturn(Future.successful(Html("")))

        val templateCaptor = ArgumentCaptor.forClass(classOf[String])
        val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

        val application = applicationBuilder.build()

        val renderer = application.injector.instanceOf[Renderer]

        renderer.render("foo", TestClassWithWrites("bar")).futureValue

        verify(mockNunjucksRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

        val json = jsonCaptor.getValue

        (json \ "config") mustBe a[JsDefined]

        application.stop()
      }
    }
  }
}

case class TestClassWithWrites(bar: String)

object TestClassWithWrites {

  implicit lazy val writes: OWrites[TestClassWithWrites] =
    Json.writes[TestClassWithWrites]
}
