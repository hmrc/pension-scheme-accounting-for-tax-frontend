package navigators

import models.{Mode, UserAnswers}
import org.scalatest.FreeSpec
import org.scalatest.prop.{PropertyChecks, TableFor3}
import pages.Page
import play.api.mvc.Call
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global

trait NavigatorBehaviour extends FreeSpec with PropertyChecks {

  implicit val hc = HeaderCarrier
  /*protected def navigatorWithRoutesForMode(mode: Mode)(navigator: CompoundNavigator,
                                                       routes: TableFor3[Page, UserAnswers, Call],
                                                       srn: String): Unit = {
    forAll(routes) {
      (id: Page, userAnswers: UserAnswers, call: Call) =>
        s"move from $id to $call in ${Mode.jsLiteral.to(mode)} with data: ${userAnswers.toString}" in {
          val result = navigator.nextPage(id, mode, userAnswers, srn)
          result mustBe call
        }
    }
  }*/

}
