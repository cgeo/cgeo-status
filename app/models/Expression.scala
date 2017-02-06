package models

import scala.util.parsing.combinator.RegexParsers

sealed trait Expression[T] {
  def interpret(versionCode: Int, versionName: String, kind: BuildKind): T
}

private object VersionCode extends Expression[Int] {
  override def interpret(versionCode: Int, versionName: String, kind: BuildKind): Int = versionCode
}

private object VersionName extends Expression[String] {
  override def interpret(versionCode: Int, versionName: String, kind: BuildKind): String = versionName
}

private object Kind extends Expression[String] {
  override def interpret(versionCode: Int, versionName: String, kind: BuildKind): String = kind.name
}

class Literal[T](a: T) extends Expression[T] {
  override def interpret(versionCode: Int, versionName: String, kind: BuildKind): T = a
}

object TrueExpression extends Literal(true)

object FalseExpression extends Literal(false)

private class Binary[T](a: Expression[T], b: Expression[T], op: (T, T) ⇒ Boolean) extends Expression[Boolean] {
  override def interpret(versionCode: Int, versionName: String, kind: BuildKind): Boolean =
    op(a.interpret(versionCode, versionName, kind), b.interpret(versionCode, versionName, kind))
}

private class Not(a: Expression[Boolean]) extends Expression[Boolean] {
  override def interpret(versionCode: Int, versionName: String, kind: BuildKind): Boolean = !a.interpret(versionCode, versionName, kind)
}

object Expression extends RegexParsers {

  private def versionCode: Parser[Expression[Int]] = "versionCode" ^^ { _ ⇒ VersionCode }
  private def intLit: Parser[Expression[Int]] = """(0|[1-9]\d*)""".r ^^ { s ⇒ new Literal(s.toInt) }
  private def int: Parser[Expression[Int]] = versionCode | intLit

  private def versionName: Parser[Expression[String]] = "versionName" ^^ { _ ⇒ VersionName }
  private def kind: Parser[Expression[String]] = "kind" ^^ { _ ⇒ Kind }
  private def strLit: Parser[Expression[String]] = """"([^"]*)"""".r ^^ { s ⇒ new Literal(s.drop(1).dropRight(1)) }
  private def str: Parser[Expression[String]] = versionName | kind | strLit

  private def bool_term: Parser[Expression[Boolean]] = t | f | int_op | str_op | bool_not | ("(" ~> bool <~ ")")
  private def bool: Parser[Expression[Boolean]] = bool_binop | bool_term
  private def t: Parser[Expression[Boolean]] = "true" ^^ { _ ⇒ TrueExpression }
  private def f: Parser[Expression[Boolean]] = "false" ^^ { _ ⇒ FalseExpression }

  private def int_op: Parser[Expression[Boolean]] = int ~ ("==" | "!=" | "<=" | "<" | ">=" | ">") ~ int ^^ {
    case a ~ "==" ~ b ⇒ new Binary(a, b, (_: Int) == (_: Int))
    case a ~ "!=" ~ b ⇒ new Binary(a, b, (_: Int) != (_: Int))
    case a ~ "<=" ~ b ⇒ new Binary(a, b, (_: Int) <= (_: Int))
    case a ~ "<" ~ b  ⇒ new Binary(a, b, (_: Int) < (_: Int))
    case a ~ ">=" ~ b ⇒ new Binary(a, b, (_: Int) >= (_: Int))
    case a ~ ">" ~ b  ⇒ new Binary(a, b, (_: Int) > (_: Int))
  }

  private def str_op: Parser[Expression[Boolean]] = str ~ ("==" | "!=" | "<=" | "<" | ">=" | ">") ~ str ^^ {
    case a ~ "==" ~ b ⇒ new Binary(a, b, (_: String) == (_: String))
    case a ~ "!=" ~ b ⇒ new Binary(a, b, (_: String) != (_: String))
    case a ~ "<=" ~ b ⇒ new Binary(a, b, (_: String) <= (_: String))
    case a ~ "<" ~ b  ⇒ new Binary(a, b, (_: String) < (_: String))
    case a ~ ">=" ~ b ⇒ new Binary(a, b, (_: String) >= (_: String))
    case a ~ ">" ~ b  ⇒ new Binary(a, b, (_: String) > (_: String))
  }

  private def bool_binop: Parser[Expression[Boolean]] = bool_term ~ ("&&" | "||" | "^") ~ bool ^^ {
    case a ~ "&&" ~ b ⇒ new Expression[Boolean] {
      override def interpret(versionCode: Int, versionName: String, kind: BuildKind): Boolean =
        if (a.interpret(versionCode, versionName, kind)) b.interpret(versionCode, versionName, kind) else false
    }
    case a ~ "||" ~ b ⇒ new Expression[Boolean] {
      override def interpret(versionCode: Int, versionName: String, kind: BuildKind): Boolean =
        if (a.interpret(versionCode, versionName, kind)) true else b.interpret(versionCode, versionName, kind)
    }
    case a ~ "^" ~ b ⇒ new Binary(a, b, (_: Boolean) ^ (_: Boolean))
  }

  private def bool_not: Parser[Expression[Boolean]] = "!" ~> bool ^^ { new Not(_) }

  def apply(input: String): ParseResult[Expression[Boolean]] = parseAll(bool, input)

  def parseError(input: Option[String]): Option[String] = input.flatMap { s ⇒
    apply(s) match {
      case Success(_, _)     ⇒ None
      case NoSuccess(msg, _) ⇒ Some(msg)
    }
  }

}
