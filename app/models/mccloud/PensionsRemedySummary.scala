package models.mccloud

case class PensionsRemedySummary(isPublicServicePensionsRemedy: Boolean,
                                 isChargeInAdditionReported: Boolean,
                                 wasAnotherPensionScheme: Boolean,
                                 pensionsRemedySchemeSummary: List[PensionsRemedySchemeSummary]) {
}
