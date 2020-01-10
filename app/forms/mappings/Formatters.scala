/*
 * Copyright 2020 HM Revenue & Customs
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

package forms.mappings

import java.text.DecimalFormat

import models.Enumerable
import play.api.data.FormError
import play.api.data.format.Formatter

import scala.util.control.Exception.nonFatalCatch
import scala.util.{Failure, Success, Try}

trait Formatters extends Transforms with Constraints {
  private[mappings] val decimalFormat = new DecimalFormat("0.00")
  private val regexPostcode = """^[A-Z]{1,2}[0-9][0-9A-Z]?\s?[0-9][A-Z]{2}$"""

  private[mappings] val optionalStringFormatter: Formatter[Option[String]] = new Formatter[Option[String]] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Option[String]] =
      Right(
        data
          .get(key)
          .map(standardiseText)
          .filter(_.lengthCompare(0) > 0)
      )

    override def unbind(key: String, value: Option[String]): Map[String, String] =
      Map(key -> value.getOrElse(""))
  }

  private[mappings] def optionalPostcodeFormatter(requiredKey: String,
                                                  invalidKey: String,
                                                  countryFieldName: String): Formatter[Option[String]] = new Formatter[Option[String]] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Option[String]] = {

      val isPostcodeRequired = data.get(countryFieldName).contains("GB")

      data.get(key) match {
        case None | Some("") if isPostcodeRequired => Left(Seq(FormError(key, requiredKey)))
        case None | Some("") => Right(None)
        case Some(postcode) =>
          if (minimiseSpace(postcode.trim.toUpperCase).matches(regexPostcode)) {
            Right(
              data
                .get(key)
                .map(standardiseText)
                .filter(_.lengthCompare(0) > 0)
            )
          } else {
            Left(Seq(FormError(key, invalidKey)))
          }
      }
    }

    override def unbind(key: String, value: Option[String]): Map[String, String] =
      Map(key -> value.getOrElse(""))
  }

  private[mappings] def stringFormatter(errorKey: String): Formatter[String] = new Formatter[String] {

    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], String] =
      data.get(key) match {
        case None | Some("") => Left(Seq(FormError(key, errorKey)))
        case Some(s) => Right(s)
      }

    override def unbind(key: String, value: String): Map[String, String] =
      Map(key -> value)
  }

  private[mappings] def booleanFormatter(requiredKey: String, invalidKey: String): Formatter[Boolean] =
    new Formatter[Boolean] {

      private val baseFormatter = stringFormatter(requiredKey)

      override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Boolean] =
        baseFormatter
          .bind(key, data)
          .right.flatMap {
          case "true" => Right(true)
          case "false" => Right(false)
          case _ => Left(Seq(FormError(key, invalidKey)))
        }

      def unbind(key: String, value: Boolean) = Map(key -> value.toString)
    }

  private[mappings] def intFormatter(requiredKey: String,
                                     wholeNumberKey: String,
                                     nonNumericKey: String,
                                     min: Option[(String, Int)] = None,
                                     max: Option[(String, Int)] = None,
                                     args: Seq[String] = Seq.empty): Formatter[Int] =
    new Formatter[Int] {

      val decimalRegexp = """^-?(\d*\.\d*)$"""

      private val baseFormatter = stringFormatter(requiredKey)

      override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Int] =
        baseFormatter
          .bind(key, data)
          .right.map(_.replace(",", ""))
          .right.flatMap {
          case s if s.matches(decimalRegexp) =>
            Left(Seq(FormError(key, wholeNumberKey, args)))
          case s =>
            Try(BigInt(s)).toOption match {
              case Some(l) if min.isDefined && l < min.get._2 => Left(Seq(FormError(key, min.get._1, args)))
              case Some(l) if max.isDefined && l > max.get._2 => Left(Seq(FormError(key, max.get._1, args)))
              case _ =>
                nonFatalCatch
                  .either(s.toInt)
                  .left.map(_ => Seq(FormError(key, nonNumericKey, args)))
            }
        }

      override def unbind(key: String, value: Int): Map[String, String] =
        baseFormatter.unbind(key, value.toString)
    }

  private[mappings] def bigDecimalFormatter(requiredKey: String,
                                            invalidKey: String,
                                            args: Seq[String] = Seq.empty): Formatter[BigDecimal] =
    new Formatter[BigDecimal] {
      private val baseFormatter = stringFormatter(requiredKey)

      override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], BigDecimal] =
        baseFormatter
          .bind(key, data)
          .right.map(_.replace(",", ""))
          .right.flatMap { s =>
          Try(BigDecimal(s)) match {
            case Success(x) => Right(x)
            case Failure(_) => Left(Seq(FormError(key, invalidKey, args)))
          }
        }

      override def unbind(key: String, value: BigDecimal): Map[String, String] =
        baseFormatter.unbind(key, value.toString)
    }

  private[mappings] def bigDecimal2DPFormatter(requiredKey: String,
                                               invalidKey: String,
                                               decimalKey: String,
                                               args: Seq[String] = Seq.empty): Formatter[BigDecimal] =
    new Formatter[BigDecimal] {
      val numericRegexp = """^-?(\-?)(\d*)(\.?)(\d*)$"""
      val decimalRegexp = """^-?(\d*\.\d{2})$"""

      private val baseFormatter = stringFormatter(requiredKey)

      override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], BigDecimal] =
        baseFormatter
          .bind(key, data)
          .right.map(_.replace(",", ""))
          .right.flatMap { s =>
          if (!s.matches(numericRegexp))
            Left(Seq(FormError(key, invalidKey, args)))
          else if (!s.matches(decimalRegexp))
            Left(Seq(FormError(key, decimalKey, args)))
          else
            Try(BigDecimal(s)) match {
              case Success(x) => Right(x)
              case Failure(_) => Left(Seq(FormError(key, invalidKey, args)))
            }
        }

      override def unbind(key: String, value: BigDecimal): Map[String, String] =
        baseFormatter.unbind(key, decimalFormat.format(value))
    }

  private[mappings] def bigDecimalTotalFormatter(itemsToTotal: String*): Formatter[BigDecimal] =
    new Formatter[BigDecimal] {

      override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], BigDecimal] = {
        val total = itemsToTotal.foldLeft[BigDecimal](BigDecimal(0)) { (acc, next) =>
          Try(BigDecimal(data.getOrElse(next, "0"))).toOption.fold(BigDecimal(0))(_ + acc)
        }
        Right(total)
      }

      override def unbind(key: String, value: BigDecimal): Map[String, String] = Map(key -> decimalFormat.format(value))
    }

  private[mappings] def enumerableFormatter[A](requiredKey: String, invalidKey: String)(implicit ev: Enumerable[A]): Formatter[A] =
    new Formatter[A] {

      private val baseFormatter = stringFormatter(requiredKey)

      override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], A] =
        baseFormatter.bind(key, data).right.flatMap {
          str =>
            ev.withName(str).map(Right.apply).getOrElse(Left(Seq(FormError(key, invalidKey))))
        }

      override def unbind(key: String, value: A): Map[String, String] =
        baseFormatter.unbind(key, value.toString)
    }
}
