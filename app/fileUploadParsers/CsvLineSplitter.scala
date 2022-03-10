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

package fileUploadParsers

import com.univocity.parsers.csv.{UnescapedQuoteHandling, CsvParser => UnivocityParser, CsvParserSettings => UnivocityParserSettings}

import java.io._
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import scala.collection.JavaConverters._

/**
 * Raw-Java style fast line splitter for CSV files. This uses the Univocity API for high performance.
 */
class CsvLineSplitter(reader: Reader) extends java.util.Iterator[Array[String]] {

  def this(is: InputStream) = this(new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8)))

  def this(string: String) = this(new StringReader(string))

  private val settings = new UnivocityParserSettings()
  settings.getFormat().setQuoteEscape('\\')
  // limits the consequence of any insane lines that crop up
 // settings.getFormat().setCharToEscapeQuoteEscaping('\\')
 // settings.setUnescapedQuoteHandling(UnescapedQuoteHandling.STOP_AT_CLOSING_QUOTE)

  private val parser = new UnivocityParser(settings)
  parser.beginParsing(reader)

  private var row: Array[String] = parser.parseNext()

  override def hasNext: Boolean = row != null

  override def next(): Array[String] = {
    val r = row
    if (row != null)
      row = parser.parseNext()
    r
  }

  def stopParsing() {
    if (row != null) {
      parser.stopParsing()
      row = null
    }
  }
}


object CsvParser {

  def split(multilineCsvString: String): Seq[Array[String]] = {
    split(new StringReader(multilineCsvString))
  }

  def split(reader: Reader): Seq[Array[String]] = {
    new CsvLineSplitter(reader).asScala.toSeq
  }

  def split(is: InputStream): Iterator[Array[String]] = {
    new CsvLineSplitter(is).asScala
  }

}

