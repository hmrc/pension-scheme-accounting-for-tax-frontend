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

package models

import play.api.libs.json.{Format, Json}

import java.time.LocalDate

case class AFTOverviewVersion(
                               numberOfVersions: Int,
                               submittedVersionAvailable: Boolean,
                               compiledVersionAvailable: Boolean
                             )

object AFTOverviewVersion {
  implicit val formats: Format[AFTOverviewVersion] = Json.format[AFTOverviewVersion]
}

case class AFTOverviewOnPODS(
                        periodStartDate: LocalDate,
                        periodEndDate: LocalDate,
                        numberOfVersions: Int,
                        submittedVersionAvailable: Boolean,
                        compiledVersionAvailable: Boolean
                      ) {
  def toFullReport: AFTOverview =
    AFTOverview(
      periodStartDate,
      periodEndDate,
      tpssReportPresent = false,
      Some(AFTOverviewVersion(numberOfVersions, submittedVersionAvailable, compiledVersionAvailable)))
}



object AFTOverviewOnPODS {
  implicit val formats: Format[AFTOverviewOnPODS] = Json.format[AFTOverviewOnPODS]
}

case class AFTOverview(
                        periodStartDate: LocalDate,
                        periodEndDate: LocalDate,
                        tpssReportPresent: Boolean,
                        versionDetails: Option[AFTOverviewVersion]
                      ) {
  def toPodsReport: AFTOverviewOnPODS = versionDetails.fold(throw ConversionCalledOnInvalidItem)(version =>
    AFTOverviewOnPODS(
      periodStartDate,
      periodEndDate,
      version.numberOfVersions,
      version.submittedVersionAvailable,
      version.compiledVersionAvailable))
}

case object ConversionCalledOnInvalidItem extends Exception("Method 'toPodsReport' should not be called on items which have only tpss report available")


object AFTOverview {
  implicit val formats: Format[AFTOverview] = Json.format[AFTOverview]
}