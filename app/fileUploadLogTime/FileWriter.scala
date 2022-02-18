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

package fileUploadLogTime

import java.io.{BufferedWriter, File, FileWriter}

object FileWriter extends App {

  /**
   * write a `String` to the `filename`.
   * First name,Last name,National Insurance number,Tax year,Charge amount,Date,Payment type mandatory
Flo,Narton,JC149717C,2020,268.28,01/01/2020,YES
Amii,Felizio,YJ920026D,2020,508.31,01/01/2020,YES
Aubrie,Wilona,GZ981439C,2020,14.13,01/01/2020,YES
Linzy,Karylin,RP005917C,2020,471.28,01/01/2020,YES
Patricia,Merriott,XX092251B,2020,700.73,01/01/2020,YES
Noelle,Ovid,XT058546B,2020,993.08,01/01/2020,YES
Tera,Sammons,KM887326D,2020,553.17,01/01/2020,YES

   */
  def writeFile(filename: String): Unit = {
    val file = new File(filename)
    val bw = new BufferedWriter(new FileWriter(file))
    bw.write("First name,Last name,National Insurance number,Tax year,Charge amount,Date,Payment type mandatory")
    bw.write("\n")
    for(index <- 0 to 13000){
      bw.write("Flo,Narton,XXXXX,2020,268.28,01/01/2020,YES")
      bw.write("\n")
    }
    bw.close()
  }
  writeFile("/home/mazin/Downloads/performance_test2_error.csv")

}
