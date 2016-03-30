import scalariform.formatter.preferences._
import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys

SbtScalariform.scalariformSettings

lazy val commonSettings = Seq(
  scalaVersion := "2.11.8",
  ScalariformKeys.preferences := ScalariformKeys.preferences.value
    .setPreference(AlignArguments, true)
    .setPreference(AlignSingleLineCaseStatements, true)
    .setPreference(DoubleIndentClassDeclaration, true)
    .setPreference(RewriteArrowSymbols, true)
    .setPreference(SpacesWithinPatternBinders, false)
    .setPreference(SpacesAroundMultiImports, false)
)

lazy val cgeoStatus = (project in file(".")).enablePlugins(PlayScala).settings(commonSettings: _*).settings(
  name := "cgeo-status",
  version := "1.1",
  libraryDependencies ++= Seq(filters, ws,
    "com.typesafe.slick" %% "slick" % "3.1.1",
    "org.postgresql" % "postgresql" % "9.4.1208"),
  routesGenerator := InjectedRoutesGenerator
).dependsOn(root)

// The subproject calls itself root
lazy val root = (project in file("libs/maxmind-geoip2-scala")).settings(commonSettings: _*)
