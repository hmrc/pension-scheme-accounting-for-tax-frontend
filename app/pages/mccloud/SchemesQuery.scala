package pages.mccloud

import models.ChargeType
import models.ChargeType.ChargeTypeLifetimeAllowance
import play.api.libs.json.{JsArray, JsPath}
import queries.Gettable

case class SchemesQuery(index :Int) extends Gettable[JsArray]{
  override def path: JsPath = JsPath \ ChargeType.chargeBaseNode(ChargeTypeLifetimeAllowance) \ "members" \ index \ "mccloudRemedy" \ "schemes"

}
