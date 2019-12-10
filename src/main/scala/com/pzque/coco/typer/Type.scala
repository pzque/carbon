package com.pzque.coco.typer

import scala.collection.mutable

sealed trait Scheme {
  val freeTypeVariables: Set[String]
}

sealed trait Type extends Scheme


case object TUnit extends Type {
  lazy val freeTypeVariables: Set[String] = Set.empty

  override def toString: String = "Unit"
}

case object TBool extends Type {
  lazy val freeTypeVariables: Set[String] = Set.empty

  override def toString: String = "Boolean"
}

case object TInt extends Type {
  lazy val freeTypeVariables: Set[String] = Set.empty

  override def toString: String = "Int"
}

case class TVar(name: String) extends Type {
  lazy val freeTypeVariables: Set[String] = Set(name)

  override def toString: String = s"$name"
}

// TODO: check currying situations carefully
// e.g. TFunc(["a","b"], ["c","d"] -> "f") should be ["a","b","c","d"] -> "f"
case class TFunc(from: Array[Type], to: Type) extends Type {
  lazy val freeTypeVariables: Set[String] = from.flatMap(_.freeTypeVariables).toSet ++ to.freeTypeVariables

  def partialApply(n: Int): TFunc = {
    val heads = from.view.slice(0, n)
    val tail = from.view.slice(n, from.length)

    val newTo = to match {
      case TFunc(fargs, fto) =>
        TFunc((tail ++ fargs).toArray, fto)
      case _ => TFunc(tail.toArray, to)
    }
    TFunc(heads.toArray[Type], newTo)
  }

  override def toString: String = {
    val fromString = TFunc.formatArgTypes(from)
    s"$fromString$to"
  }

  // TODO: optimize here
  override def equals(obj: Any): Boolean = {
    obj.isInstanceOf[TFunc] && obj.toString == toString
  }
}

object TFunc {
  def formatArgTypes(argTypes: Array[Type]): String = {
    val builder = new mutable.StringBuilder
    argTypes foreach { t =>
      val repr = if (t.isInstanceOf[TFunc]) s"($t) -> " else s"$t -> "
      builder.append(repr)
    }
    builder.toString()
  }
}

case class Generic(name: String, params: Array[Type]) extends Type {
  override lazy val freeTypeVariables: Set[String] = params.flatMap(_.freeTypeVariables).toSet

  override def toString: String = s"""$name ${params.mkString(" ")}"""

  override def equals(obj: Any): Boolean = {
    obj.isInstanceOf[Generic] && obj.toString == toString
  }
}

/**
  * This class is used to achieve let polymorphism.
  * e.g.
  * let f = \x => x in f f
  * *   |            --| |--
  * *  1st          2nd  3rd
  * There 3 'f' occurs in above let expression, we first generalize the 1st 'f' as a 'PolyType'
  * then the 2nd and 3rd 'f' will be instantiated with two different types.
  * Thus the type inference procedure could continue.
  *
  * Here `f f` means apply a function of type `T1 -> T1` to a parameter of type `T2 -> T2`.
  * Rather than apply a `T -> T` function to a `T -> T` type, which is obviously an error.
  *
  * @param vars
  * @param body
  */
case class PolyType(vars: Array[String], body: Type) extends Scheme {
  lazy val freeTypeVariables: Set[String] = body.freeTypeVariables -- vars

  override def toString: String = vars.mkString("$", ".$", s" => ${body.toString}")
}
