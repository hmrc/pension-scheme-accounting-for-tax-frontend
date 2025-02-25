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

import connectors.AFTConnector
import controllers.base.ControllerSpecBase
import data.SampleData
import data.SampleData.{schemeDetails, srn}
import models.viewModels.PastAftReturnsViewModel
import models.{AFTOverview, AFTOverviewVersion, AFTQuarter, Enumerable, PastAftReturnGroup, ReportLink}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import play.api.Application
import play.api.http.Status.OK
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.mvc.Results
import play.api.test.Helpers.{defaultAwaitTimeout, route, status, writeableOf_AnyContentAsEmpty}
import play.twirl.api.Html
import services.SchemeService
import views.html.PastAFTReturnsView

import java.time.LocalDate
import scala.annotation.tailrec
import scala.concurrent.Future

class PastAftReturnsControllerSpec extends ControllerSpecBase with BeforeAndAfterEach
  with Enumerable.Implicits with Results with ScalaFutures {

  private val mockSchemeService = mock[SchemeService]
  private val mockAFTConnector: AFTConnector = mock[AFTConnector]

  private val extraModules: Seq[GuiceableModule] =
    Seq[GuiceableModule](
      bind[SchemeService].toInstance(mockSchemeService),
      bind[AFTConnector].toInstance(mockAFTConnector)
    )

  private val application: Application = applicationBuilder(extraModules = extraModules).build()

  private val httpPathGET = controllers.routes.PastAftReturnsController.onPageLoad(srn, 0).url

  override def beforeEach(): Unit = {
    super.beforeEach()
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
    when(mockSchemeService.retrieveSchemeDetails(any(), any())(any(), any())).thenReturn(Future.successful(schemeDetails))
  }

  "PastAftReturnsController" must {
    "successfully render correct view when fewer than 4 years of past AFT returns are available" in {

      val sampleData = generateSampleData(1)

      when(mockAFTConnector.getAftOverview(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(sampleData))

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      val groupedReturns = generateGroupedReturns(1)

      val view = application.injector.instanceOf[PastAFTReturnsView].apply(
        SampleData.schemeName,
        PastAftReturnsViewModel(groupedReturns),
        0,
        controllers.routes.PastAftReturnsController.onPageLoad(srn, 1).url,
        controllers.routes.PastAftReturnsController.onPageLoad(srn, 2).url,
        controllers.routes.AFTOverviewController.onPageLoad(srn).url
      )(httpGETRequest(httpPathGET), messages)

      compareResultAndView(result, view)
    }
    "successfully render correct view when more than 4 years of past AFT returns are available" in {

      val sampleData = generateSampleData(5)

      when(mockAFTConnector.getAftOverview(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(sampleData))


      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      val groupedReturns = generateGroupedReturns(4)

      val view = application.injector.instanceOf[PastAFTReturnsView].apply(
        SampleData.schemeName,
        PastAftReturnsViewModel(groupedReturns),
        1,
        controllers.routes.PastAftReturnsController.onPageLoad(srn, 1).url,
        controllers.routes.PastAftReturnsController.onPageLoad(srn, 2).url,
        controllers.routes.AFTOverviewController.onPageLoad(srn).url
      )(httpGETRequest(httpPathGET), messages)

      compareResultAndView(result, view)
    }
  }

  def getQuarterDates(year: Int): AFTQuarter = {
    AFTQuarter(LocalDate.of(year, 1, 1), LocalDate.of(year, 3, 31))
  }

  def generateSampleData(requiredYears: Int): List[AFTOverview] = {

    val currentYear = LocalDate.now().getYear

    val yearRange = Seq.range(currentYear - requiredYears, currentYear).toList

    @tailrec
    def aux(years: List[Int], aftOverviews: List[AFTOverview]): List[AFTOverview] = {
      if (years.isEmpty) {
        aftOverviews
      } else {
        val quarterDates = getQuarterDates(years.head)
        val aftOverview = AFTOverview(quarterDates.startDate, quarterDates.endDate,
          tpssReportPresent = false,
          Some(AFTOverviewVersion(numberOfVersions = 1, submittedVersionAvailable = true, compiledVersionAvailable = false))
        )
        aux(years.tail, aftOverviews ++ List(aftOverview))
      }
    }

    aux(yearRange, List.empty)
  }

  def generateGroupedReturns(requiredYears: Int): List[PastAftReturnGroup] = {
    val currentYear = LocalDate.now().getYear

    val yearRange = Seq.range(currentYear - requiredYears, currentYear).toList.reverse

    @tailrec
    def aux(years: List[Int], groupedReturns: List[PastAftReturnGroup]): List[PastAftReturnGroup] = {
      if (years.isEmpty) {
        groupedReturns
      } else {
        val quarterStartDate = getQuarterDates(years.head).startDate.toString
        val reportLinks = List(ReportLink(s"1 January to 31 March ${years.head.toString}", controllers.amend.routes.ReturnHistoryController.onPageLoad(srn, quarterStartDate).url))
        val returnGroup = PastAftReturnGroup(s"${years.head}", reportLinks)
        aux(years.tail, groupedReturns ++ List(returnGroup))
      }
    }

    aux(yearRange, List.empty)
  }
}
