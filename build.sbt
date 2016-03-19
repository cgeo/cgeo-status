lazy val commonSettings = Seq(
  scalaVersion := "2.11.8"
)

lazy val cgeoStatus = (project in file(".")).enablePlugins(PlayScala).settings(commonSettings: _*).settings(
  name := "cgeo-status",
  version := "1.1",
  libraryDependencies ++= Seq(filters, ws, "org.mongodb" %% "casbah-core" % "3.1.1"),
  routesGenerator := InjectedRoutesGenerator
).dependsOn(root)

// The subproject calls itself root
lazy val root = (project in file("libs/maxmind-geoip2-scala")).settings(commonSettings: _*)
