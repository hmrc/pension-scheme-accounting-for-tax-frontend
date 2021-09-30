/*
 * Copyright 2021 HM Revenue & Customs
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

package utils

import play.api.libs.json._

trait JsLens {
  self =>

  def get(s: JsValue): JsResult[JsValue] = {
    getAll(s).map {
      case Seq(value) =>
        value
      case Seq(values@_*) =>
        JsArray(values)
    }
  }

  def getAll(s: JsValue): JsResult[Seq[JsValue]]

  def set(a: JsValue, s: JsValue): JsResult[JsValue]

  def remove(s: JsValue): JsResult[JsValue]

  def andThen(other: JsLens): JsLens =
    new JsLens {

      override def getAll(s: JsValue): JsResult[Seq[JsValue]] = {
        self.getAll(s).flatMap {
          as =>
            traverse(as.flatMap {
              a =>
                traverse(other.getAll(a))
            })
        }
      }

      override def set(b: JsValue, s: JsValue): JsResult[JsValue] = {
        self.getAll(s).recover {
          case e if e.errors.exists {
            _._2.exists {
              error =>
                error.message.contains("undefined") ||
                  error.message.contains("out of bounds")
            }
          } => Seq(JsNull)
        }.flatMap {
          case Seq(a) =>
            other.set(b, a).flatMap(self.set(_, s))
          case as =>
            traverse(as.map(other.set(b, _))).flatMap {
              case Seq(jsValue) =>
                self.set(jsValue, s)
              case _ =>
                JsError("cannot set with traversal")
            }
        }
      }

      override def remove(s: JsValue): JsResult[JsValue] = {
        self.getAll(s).flatMap {
          case Seq(a) =>
            other.remove(a).flatMap(self.set(_, s))
          case as =>
            traverse(as.map(other.remove)).flatMap {
              case Seq(jsValue) =>
                self.set(jsValue, s)
              case _ =>
                JsError("cannot remove with traversal")
            }
        }
      }
    }

  private def traverse(seq: Seq[JsResult[JsValue]]): JsResult[Seq[JsValue]] = {
    seq match {
      case Seq(e@JsError(_)) =>
        e
      case _ =>
        // should we favour successes or failures?
        JsSuccess(seq.foldLeft(Seq.empty[JsValue]) {
          case (m, JsSuccess(n, _)) =>
            m :+ n
          case (m, _) =>
            m
        })
    }
  }

  private def traverse(opt: JsResult[Seq[JsValue]]): Seq[JsResult[JsValue]] = {
    opt match {
      case JsSuccess(value, _) =>
        value.map(JsSuccess(_))
      case e: JsError =>
        Seq(e)
    }
  }
}

object JsLens {

  def fromPath(path: JsPath): JsLens = {

    def toLens(node: PathNode): JsLens = {
      node match {
        case KeyPathNode(key) =>
          JsLens.atKey(key)
        case IdxPathNode(idx) =>
          JsLens.atIndex(idx)
        case RecursiveSearch(key) =>
          JsLens.atAllIndices andThen JsLens.atKey(key)
      }
    }

    path.path.map(toLens).reduceLeft(_ andThen _)
  }

  def atKey(key: String): JsLens =
    new JsLens {

      override def getAll(outer: JsValue): JsResult[Seq[JsValue]] = {
        (outer \ key).validate[JsValue].map(Seq(_))
      }

      override def set(inner: JsValue, outer: JsValue): JsResult[JsValue] = {
        outer match {
          case obj: JsObject =>
            JsSuccess(obj ++ Json.obj(key -> inner))
          case JsNull =>
            JsSuccess(Json.obj(key -> inner))
          case _ =>
            JsError("Not an object")
        }
      }

      override def remove(outer: JsValue): JsResult[JsValue] =
        outer match {
          case obj: JsObject =>
            JsSuccess(obj - key)
          case JsNull =>
            JsSuccess(JsNull)
          case _ =>
            JsError("Not an object")
        }
    }

  def atIndex(index: Int): JsLens =
    new JsLens {

      require(index >= 0)

      override def getAll(outer: JsValue): JsResult[Seq[JsValue]] = {
        (outer \ index).validate[JsValue].map(Seq(_))
      }

      override def set(inner: JsValue, outer: JsValue): JsResult[JsValue] = {
        outer match {
          case JsArray(values) if index < values.size =>
            JsSuccess(JsArray(values.patch(index, Seq(inner), 1)))
          case JsArray(values) if index == values.size =>
            JsSuccess(JsArray(values :+ inner))
          case JsNull if index == 0 =>
            JsSuccess(JsArray(Seq(inner)))
          case JsArray(_) =>
            JsError("Index out of bounds")
          case _ =>
            JsError("Not an array")
        }
      }

      override def remove(outer: JsValue): JsResult[JsValue] = {
        outer match {
          case JsArray(values) =>
            JsSuccess(JsArray(values.patch(index, Seq.empty, 1)))
          case JsNull =>
            JsSuccess(JsNull)
          case _ =>
            JsError("Not an array")
        }
      }
    }

  def atAllIndices: JsLens =
    new JsLens {

      override def getAll(s: JsValue): JsResult[Seq[JsValue]] =
        s match {
          case JsArray(values) =>
            JsSuccess(values)
          case JsNull =>
            JsSuccess(Seq(JsNull))
          case _ =>
            JsError("Not an array")
        }

      override def set(a: JsValue, s: JsValue): JsResult[JsValue] =
        s match {
          case JsArray(values) =>
            JsSuccess(JsArray(values.map(_ => a)))
          case JsNull =>
            JsSuccess(JsArray(Seq(a)))
          case _ =>
            JsError("Not an array")
        }

      override def remove(s: JsValue): JsResult[JsValue] = ???
    }
}

