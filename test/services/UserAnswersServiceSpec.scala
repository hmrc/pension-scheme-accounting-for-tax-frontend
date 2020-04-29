package services

import base.SpecBase
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar

class UserAnswersServiceSpec extends SpecBase with MockitoSugar with ScalaFutures {

  ".set" must {
    "set amended version to null and the page value for a scheme level charge being added if version is 2" in {

    }

    "set amended version to null and the page value for a scheme level charge being added if version is 2" in {

    }

    "set amended version to null and the page value for a scheme level charge being deleted if version is 2" in {

    }

    "set only the page value for a scheme level charge being added if version is 1" in {

    }

    "set only the page value for a scheme level charge being deleted if version is 1" in {

    }

    "set only the page value for a member level charge being added if version is 1" in {

    }

    "set only the page value for a member level charge being changed if version is 1" in {

    }

    "set only the page value for a member level charge being deleted if version is 1" in {

    }

    "set amended version, member version to null, status to New and the page value" +
      " for a scheme level charge if version is 2 for a new member being added" in {

    }

    "set amended version, member version to null, status to Changed and the page value" +
      " for a scheme level charge if version is 2 for a member being changed" in {

    }

    "set amended version, member version to null, status to Deleted and the page value" +
      " for a scheme level charge if version is 2 for a member being deleted" in {

    }
  }
}
