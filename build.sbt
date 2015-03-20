lazy val cgeoStatus = (project in file(".")).enablePlugins(play.PlayScala).settings(
  name := "cgeo-status",
  version := "1.1",
  scalaVersion := "2.11.6",
  libraryDependencies +=  "org.mongodb" %% "casbah-core" % "2.8.0"
)
