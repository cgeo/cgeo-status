import models.{Expression, NightlyBuild}
import org.scalatestplus.play._

class ExpressionSpec extends PlaySpec {

  def checkResult(expr: String, expected: Boolean): Unit = {
    Expression(expr).get.interpret(20170201, "2017.02.01", NightlyBuild) mustBe expected
  }

  def checkTrue(expr: String): Unit = {
    checkResult(expr, true)
  }

  def checkFalse(expr: String): Unit = {
    checkResult(expr, false)
  }

  "Expression.apply()" must {

    "reject unparsable expressions" in {
      Expression("foo").isInstanceOf[Expression.Failure] mustBe true
    }

    "recognize boolean literals" in {
      checkTrue("true")
      checkFalse("false")
    }

    "recognize integer literals" in {
      checkTrue("1 == 1")
      checkFalse("1 == 2")
    }

    "recognize string literals" in {
      checkTrue(""""abc" == "abc"""")
      checkFalse(""""abc" == "def"""")
    }

    "compose boolean values" in {
      checkTrue("true || false")
      checkFalse("true && false")
      checkTrue("true ^ false")
      checkTrue("!false")
      checkFalse("!true")
    }

    "honor parentheses" in {
      checkTrue("(((true)))")
      checkFalse("(((false)))")
      checkTrue("true ^ (false && false)")
      checkFalse("(true ^ false) && false")
      checkFalse("true ^ !(false && false)")
      checkFalse("(true ^ false) && !true")
    }

    "be able to compare versionCode" in {
      checkFalse("versionCode == 20170101")
      checkTrue("versionCode == 20170201")
      checkTrue("versionCode != 20170101")
      checkFalse("versionCode != 20170201")
      checkFalse("versionCode <= 20170101")
      checkTrue("versionCode <= 20170201")
      checkTrue("versionCode <= 20170301")
      checkFalse("versionCode < 20170101")
      checkFalse("versionCode < 20170201")
      checkTrue("versionCode < 20170301")
      checkTrue("versionCode >= 20170101")
      checkTrue("versionCode >= 20170201")
      checkFalse("versionCode >= 20170301")
      checkTrue("versionCode > 20170101")
      checkFalse("versionCode > 20170201")
      checkFalse("versionCode > 20170301")
    }

    "be able to compare versionName" in {
      checkFalse("""versionName == "2017.01.01"""")
      checkTrue("""versionName == "2017.02.01"""")
      checkTrue("""versionName != "2017.01.01"""")
      checkFalse("""versionName != "2017.02.01"""")
      checkFalse("""versionName <= "2017.01.01"""")
      checkTrue("""versionName <= "2017.02.01"""")
      checkTrue("""versionName <= "2017.03.01"""")
      checkFalse("""versionName < "2017.01.01"""")
      checkFalse("""versionName < "2017.02.01"""")
      checkTrue("""versionName < "2017.03.01"""")
      checkTrue("""versionName >= "2017.01.01"""")
      checkTrue("""versionName >= "2017.02.01"""")
      checkFalse("""versionName >= "2017.03.01"""")
      checkTrue("""versionName > "2017.01.01"""")
      checkFalse("""versionName > "2017.02.01"""")
      checkFalse("""versionName > "2017.03.01"""")
    }

    "be able to compare kind" in {
      checkTrue("""kind == "nightly"""")
      checkFalse("""kind == "release"""")
    }

  }

}
