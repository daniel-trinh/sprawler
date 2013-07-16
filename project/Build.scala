import sbt._
import Keys._
import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys

object ApplicationBuild extends Build {

  import Dependencies._
  import Resolvers._

  val appName         = "webcrawler"
  val appVersion      = "0.1.0"

  val appDependencies = sprayDeps ++ akkaDeps ++ miscDeps ++ testDeps ++ crawlerDeps ++ Seq(
    "org.scala-lang" % "scala-compiler" % "2.10.1"
  )
  val appResolvers = Seq(
    jboss,
    scalaTools,
    typesafe,
    spray,
    sprayNightly,
    sonatype
  )

  val scalacSettings = Seq("-Dscalac.patmat.analysisBudget=off", "-feature")

  lazy val async = Project(
    id   = "async",
    base = file("../webcrawler/async")
  )

  val main = play.Project(
    appName, appVersion, appDependencies
  ).settings(
    SbtScalariform.scalariformSettings ++
      (resolvers ++= appResolvers) ++
      (scalaVersion := "2.10.1") ++
      (scalacOptions ++= scalacSettings) ++
      (ScalariformKeys.preferences := formattingPreferences): _*
  ) aggregate(async) dependsOn(async)

  val formattingPreferences = {
    import scalariform.formatter.preferences._
    FormattingPreferences()
      .setPreference(PreserveDanglingCloseParenthesis, true)
      .setPreference(AlignParameters, false)
      .setPreference(CompactStringConcatenation, true)
      .setPreference(CompactControlReadability, false)
      .setPreference(AlignSingleLineCaseStatements, true)
      .setPreference(AlignSingleLineCaseStatements.MaxArrowIndent, 40)
      .setPreference(SpacesWithinPatternBinders, true)
      .setPreference(DoubleIndentClassDeclaration, true)
  }
}

object Resolvers {
  val jboss      = "JBoss repository" at
    "https://repository.jboss.org/nexus/content/repositories/"
  val scalaTools = "Scala-Tools Maven2 Snapshots Repository" at
    "http://scala-tools.org/repo-snapshots"
  val typesafe   = "Typesafe Repository" at
    "http://repo.typesafe.com/typesafe/releases/"
  val spray      = "spray repo" at
    "http://repo.spray.io"
  val sprayNightly = "spray nightly" at
    "http://nightlies.spray.io"
  val sonatype   = "Sonatype OSS" at
    "https://oss.sonatype.org"
}

object Dependencies {

  object V {
    val Spray = "1.1-M8"
    val SprayNightly = "1.1-20130614"
    val Akka  = "2.1.4"
  }

  // Misc
  val miscDeps = Seq(
    "com.typesafe"                    %  "config"          % "1.0.0",
    "com.github.nscala-time"          %% "nscala-time"     % "0.2.0"            
  )
  val Seq(typesafeConfig, nscalaTime) = miscDeps

  // Webcrawler related dependencies
  val crawlerDeps = Seq(
    "com.google.code.crawler-commons" % "crawler-commons"  % "0.2",
    "com.google.guava"                % "guava"            % "r09",
    "org.jsoup"                       % "jsoup"            % "1.7.2"
  )
  val Seq(crawlerCommons, guava, jsoup) = crawlerDeps

  // Akka
  val akkaDeps = Seq(
    "com.typesafe.akka" %% "akka-agent" % V.Akka,
    "com.typesafe.akka" %% "akka-actor" % V.Akka,
    "com.typesafe.akka" %% "akka-contrib" % V.Akka
  )
  val Seq(akkaAgent, akkaActor, akkaContrib) = akkaDeps

  // Spray
  val sprayDeps = Seq(
    "io.spray" %  "spray-client" % V.SprayNightly,
    "io.spray" %  "spray-can"    % V.SprayNightly,
    "io.spray" %  "spray-http"   % V.SprayNightly,
    "io.spray" %  "spray-httpx"  % V.SprayNightly,
    "io.spray" %  "spray-util"   % V.SprayNightly
  )
  val Seq(
    sprayClient,
    sprayCan,
    sprayHttp,
    sprayHttpx,
    sprayUtil
  ) = sprayDeps

  // Testing dependencies
  val testDeps = Seq(
    "com.typesafe.akka" %% "akka-testkit"                % V.Akka % "test",
    "org.scalatest"     %  "scalatest_2.10"              % "1.9.1" % "test",
    "org.scalamock"     %% "scalamock-scalatest-support" % "3.0.1" % "test"
  )
  val Seq(akkaTestkit, scalaTest, scalaMock) = testDeps
}