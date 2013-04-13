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
    sonatype
  )

  val scalacSettings = Seq("-Dscalac.patmat.analysisBudget=off")

  val main = play.Project(
    appName, appVersion, appDependencies
  ).settings(
    SbtScalariform.scalariformSettings ++
      (resolvers ++= appResolvers) ++
      (scalaVersion := "2.10.1") ++
      (scalacOptions ++= scalacSettings) ++
      (ScalariformKeys.preferences := formattingPreferences): _*
  )

  val formattingPreferences = {
    import scalariform.formatter.preferences._
    FormattingPreferences()
      .setPreference(DoubleIndentClassDeclaration, false)
      .setPreference(PreserveDanglingCloseParenthesis, true)
      .setPreference(AlignParameters, true)
      .setPreference(AlignSingleLineCaseStatements, true)
      .setPreference(AlignSingleLineCaseStatements.MaxArrowIndent, 40)
      .setPreference(DoubleIndentClassDeclaration, true)
      .setPreference(PreserveDanglingCloseParenthesis, true)
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
  val sonatype   = "Sonatype OSS" at
    "https://oss.sonatype.org"
}

object Dependencies {

  object V {
    val Spray = "1.1-M7"
    val Akka  = "2.1.2"
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
    "com.google.guava"                % "guava"            % "r09"
  )
  val Seq(crawlerCommons, guava) = crawlerDeps

  // Akka
  val akkaDeps = Seq(
    "com.typesafe.akka" %% "akka-agent" % V.Akka,
    "com.typesafe.akka" %% "akka-actor" % V.Akka
  )
  val Seq(akkaAgent, akkaActor) = akkaDeps

  // Spray
  val sprayDeps = Seq(
    "io.spray" %  "spray-client" % V.Spray,
    "io.spray" %  "spray-can"    % V.Spray,
    "io.spray" %  "spray-http"   % V.Spray,
    "io.spray" %  "spray-httpx"  % V.Spray,
    "io.spray" %  "spray-util"   % V.Spray
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