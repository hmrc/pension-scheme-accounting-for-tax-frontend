/*
 * Copyright 2023 HM Revenue & Customs
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

package services

import models.ChargeType
import play.api.Environment

import java.io.File
import javax.inject.Inject

class FileProviderService @Inject()(environment: Environment){
  private val baseInstructionsPath: String = "conf/resources/fileDownload/instructions"
  private val baseTemplatePath: String = "conf/resources/fileDownload/template"

  private val instructionsFilePathMap = Map(
    "annualAllowancePSR" -> s"$baseInstructionsPath/instructions_aft-annual-allowance-charge-upload-format.ods",
    "annualAllowance" -> s"$baseInstructionsPath/instructions_aft-annual-allowance-charge-upload-non-public-service-pensions-remedy.ods",
    "lifeTimeAllowancePSR" -> s"$baseInstructionsPath/instructions_aft-lifetime-allowance-charge-upload-format.ods",
    "lifeTimeAllowance" -> s"$baseInstructionsPath/instructions_aft-lifetime-allowance-charge-upload-non-public-service-pensions-remedy.ods",
    "overseasTransfer"-> s"$baseInstructionsPath/aft-overseas-transfer-charge-upload-format-instructions.ods"
  )

  private val templateFilePathMap = Map(
    "annualAllowancePSR" -> s"$baseTemplatePath/upload-template_aft-annual-allowance-charge.csv",
    "annualAllowance" -> s"$baseTemplatePath/upload-template_aft-annual-allowance-charge_non-public-service-pensions-remedy.csv",
    "lifeTimeAllowancePSR"-> s"$baseTemplatePath/upload-template_aft-lifetime-allowance-charge.csv",
    "lifeTimeAllowance"-> s"$baseTemplatePath/upload-template_aft-lifetime-allowance-charge_non-public-service-pensions-remedy.csv",
    "overseasTransfer"-> s"$baseTemplatePath/aft-overseas-transfer-charge-upload-template.csv"
  )

  private def instructionsFilePath(chargeType: ChargeType, psr: Option[Boolean]): String = {
    psr match {
      case Some(true) => instructionsFilePathMap(s"${chargeType.toString}PSR")
      case _ => instructionsFilePathMap(chargeType.toString)
    }
  }

  private def templateFilePath(chargeType: ChargeType, psr: Option[Boolean]): String = {
    psr match {
      case Some(true) => templateFilePathMap(s"${chargeType.toString}PSR")
      case _ => templateFilePathMap(chargeType.toString)
    }
  }

  def getInstructionsFile(chargeType: ChargeType, psr: Option[Boolean]): File  = {
    val path: String = instructionsFilePath(chargeType, psr)
    environment.getFile(path)
  }

  def getTemplateFile(chargeType: ChargeType, psr: Option[Boolean]): File = {
    val path: String = templateFilePath(chargeType, psr)
    environment.getFile(path)
  }
}