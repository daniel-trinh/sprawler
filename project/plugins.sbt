// Comment to get more information during initialization
logLevel := Level.Warn

// The Typesafe repository 
resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

// Use the Play sbt plugin for Play projects
addSbtPlugin("play" % "sbt-plugin" % "2.1.2")

addSbtPlugin("com.typesafe.sbt" % "sbt-scalariform" % "1.0.1")

addSbtPlugin("com.orrsella" % "sbt-sound" % "1.0.1")