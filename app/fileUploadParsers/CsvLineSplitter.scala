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


import com.univocity.parsers.common.record.Record
import com.univocity.parsers.csv.{CsvParser, CsvParserSettings}

import java.io._
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import scala.collection.JavaConverters._




object CsvParser {

  def split(multilineCsvString: String): Seq[Array[String]] = {
    val settings = new CsvParserSettings()
    settings.setNullValue("")
    val parser = new CsvParser(settings)
    parser.parseAll(new StringReader(multilineCsvString)).asScala
  }

  def splitToRecord(multilineCsvString: String): Seq[Record] = {
    val settings = new CsvParserSettings()
    settings.setEscapeUnquotedValues(true)
    settings.setKeepEscapeSequences(true)
    //  settings.setKeepQuotes(true)
    settings.setDelimiterDetectionEnabled(true)
    settings.setQuoteDetectionEnabled(true)
    val parser = new CsvParser(settings)
    parser.parseAllRecords(new StringReader(multilineCsvString)).asScala
  }


}

