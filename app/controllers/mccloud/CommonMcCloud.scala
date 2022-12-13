package controllers.mccloud

import models.requests.DataRequest
import play.api.i18n.{I18nSupport, Messages}
import play.api.mvc.AnyContent

trait CommonMcCloud extends I18nSupport {
  protected def ordinal(index: Int)(implicit request: DataRequest[AnyContent]): Option[String] = {
    if (index < 0 | index > 4) {
      None
    } else {
      Some(Messages(s"mccloud.scheme.ref$index"))
    }
  }
}
