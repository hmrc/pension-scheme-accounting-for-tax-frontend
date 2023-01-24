/*
 * Copyright 2023 HM Revenue & Customs
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

///*
// * Copyright 2023 HM Revenue & Customs
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package utils
//
//import fileUploadParsers.Parser.Result
//
//trait Semigroup[T] {
//  def combine(a: T, b: T): T
//
//  def unit: T
//}
//
//object Semigroup {
//  implicit val resultSemigroup: Semigroup[Result] = new Semigroup[Result] {
//    def combine(a: Result, b: Result): Result = {
//      (a, b) match {
//        case (Left(x), Left(y)) => Left(x ++ y)
//        case (x@Left(_), Right(_)) => x
//        case (Right(_), x@Left(_)) => x
//        case (Right(x), Right(y)) => Right(x ++ y)
//      }
//    }
//
//    def unit: Result = Right(Nil)
//  }
//}
