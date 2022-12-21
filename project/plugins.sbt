resolvers += "Typesafe repository" at "https://repo.typesafe.com/typesafe/releases/"

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.6.20")

addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.8.2")

// Scala 2.12.17 which is used by default in sbt 1.8.0 would require
// a more recent version of scala-xml than the scalariform and play plugins
// support, so force the version for sbt to 2.12.16.
scalaVersion := "2.12.16"
