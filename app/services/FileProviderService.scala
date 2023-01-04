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

  private val instructionsFilePath = Map(
    "annualAllowance" -> s"$baseInstructionsPath/aft-annual-allowance-charge-upload-format-instructions.ods",
    "lifeTimeAllowance" -> s"$baseInstructionsPath/aft-lifetime-allowance-charge-upload-format-instructions.ods",
    "overseasTransfer"-> s"$baseInstructionsPath/aft-overseas-transfer-charge-upload-format-instructions.ods"
  )

  private val templateFilePathMap = Map(
    "annualAllowance" -> s"$baseTemplatePath/aft-annual-allowance-charge-upload-template.csv",
    "lifeTimeAllowance"-> s"$baseTemplatePath/aft-lifetime-allowance-charge-upload-template.csv",
    "overseasTransfer"-> s"$baseTemplatePath/aft-overseas-transfer-charge-upload-template.csv"
  )

  private def instructionsFilePath(chargeType: ChargeType): String = {
    instructionsFilePath(chargeType.toString)
  }

  private def templateFilePath(chargeType: ChargeType): String = {
    templateFilePathMap(chargeType.toString)
  }


  def getInstructionsFile(chargeType: ChargeType): File  = {
    val path: String = instructionsFilePath(chargeType)
    environment.getFile(path)
  }

  def getTemplateFile(chargeType: ChargeType): File = {
    val path: String = templateFilePath(chargeType)
    environment.getFile(path)
  }
}