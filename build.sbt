import scalariform.formatter.preferences._
import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys

// SbtScalariform.scalariformSettings(true)

lazy val commonSettings = Seq(
  scalaVersion := "2.12.6",
  ScalariformKeys.preferences := ScalariformKeys.preferences.value
    .setPreference(AlignArguments, true)
    .setPreference(AlignSingleLineCaseStatements, true)
    .setPreference(DoubleIndentConstructorArguments, true)
    .setPreference(RewriteArrowSymbols, true)
    .setPreference(SpacesWithinPatternBinders, false)
    .setPreference(SpacesAroundMultiImports, false)
)

lazy val cgeoStatus = (project in file(".")).enablePlugins(PlayScala).settings(commonSettings: _*).settings(
  name := "cgeo-status",
  version := "1.1",
  libraryDependencies ++= Seq(filters, ws, guice,
    "com.typesafe.slick" %% "slick" % "3.2.3",
    "org.postgresql" % "postgresql" % "42.2.2",
    "com.google.api-client" % "google-api-client" % "1.20.0",
    "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2" % "test"),
  routesGenerator := InjectedRoutesGenerator
).dependsOn(geoip2)

lazy val geoip2 = RootProject(file("external/maxmind-geoip2-scala"))
