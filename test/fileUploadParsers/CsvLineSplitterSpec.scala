package fileUploadParsers

import base.SpecBase
import org.mockito.MockitoSugar
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.must.Matchers

class CsvLineSplitterSpec extends SpecBase with Matchers with MockitoSugar with BeforeAndAfterEach {

  "LifeTime allowance parser" must {
    "return charges in user answers when there are no validation errors" in {
      val lines = """Year,Make,Model,Description,Price
                    1997,Ford,E350,"ac, abs, moon",3000.00
                    1999,Chevy,"Venture ""Extended Edition\"\"\","",4900.00"""
      CsvParser.split(lines)
    }
  }

}
