package models.mccloud

import models.{AFTQuarter, YearRange}
case class PensionsRemedySchemeSummary(pstrNumber: String,
                                       taxYear: YearRange,
                                       taxQuarter: AFTQuarter,
                                       chargeAmountReported: BigDecimal) {

}
