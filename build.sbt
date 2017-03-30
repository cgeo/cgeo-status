import scalariform.formatter.preferences._
import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys

SbtScalariform.scalariformSettings

lazy val commonSettings = Seq(
  scalaVersion := "2.11.9",
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
  scalaVersion := "2.11.8",
  libraryDependencies ++= Seq(filters, ws,
    "com.typesafe.slick" %% "slick" % "3.2.0",
    "org.postgresql" % "postgresql" % "42.0.0",
    "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.0" % "test"),
  routesGenerator := InjectedRoutesGenerator
).dependsOn(geoip2)

lazy val geoip2 = RootProject(file("external/maxmind-geoip2-scala"))
