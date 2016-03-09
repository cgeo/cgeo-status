lazy val cgeoStatus = (project in file(".")).enablePlugins(PlayScala).settings(
  name := "cgeo-status",
  version := "1.1",
  scalaVersion := "2.11.8",
  libraryDependencies ++=  Seq(filters, "org.mongodb" %% "casbah-core" % "3.1.1"),
  routesGenerator := InjectedRoutesGenerator
)
