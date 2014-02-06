// Comment to get more information during initialization
logLevel := Level.Warn

// The Typesafe repository
resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

resolvers += Resolver.url("sbt-plugin-releases-scalasbt", url("http://repo.scala-sbt.org/scalasbt/sbt-plugin-releases/"))(Resolver.ivyStylePatterns)

resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

resolvers += "spray repo" at "http://repo.spray.io"

resolvers += Classpaths.typesafeResolver

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.2.0")

addSbtPlugin("com.danieltrinh" % "sbt-scalariform" % "1.3.0-SNAPSHOT")

addSbtPlugin("com.typesafe.sbt" % "sbt-atmos" % "0.2.3")

addSbtPlugin("com.typesafe.sbt" % "sbt-proguard" % "0.2.2")

addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.5.2")

addSbtPlugin("com.tuplejump" % "sbt-yeoman" % "0.6.2")

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.10.1")

addSbtPlugin("de.johoop" % "jacoco4sbt" % "2.1.4")