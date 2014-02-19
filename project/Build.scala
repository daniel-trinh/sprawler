import sbt._
import Keys._
import com.tuplejump.sbt.yeoman.Yeoman
import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import com.typesafe.sbt.SbtAtmos.{ Atmos, atmosSettings }
import de.johoop.jacoco4sbt._
import JacocoPlugin._

object ApplicationBuild extends Build {

  import Dependencies._
  import Resolvers._

  val appName         = "Sprawler"
  val appVersion      = "0.2.0"


  val appDependencies = sprayDeps ++
    playDeps ++
    akkaDeps ++
    miscDeps ++
    testDeps ++
    crawlerDeps ++
    Seq(
      "org.scala-lang" % "scala-compiler" % V.Scala
    )

  val appResolvers = Seq(
    jboss,
    scalaTools,
    typesafe,
    spray,
    sprayNightly,
    sonatype
  )

  val scalacSettings = Seq(
    "-Dscalac.patmat.analysisBudget=off",
    "-feature"
    // Uncomment this line to help find initialization order problems
    // "-Xcheckinit"
  )

  val formattingPreferences = {
    import scalariform.formatter.preferences._
    FormattingPreferences()
      .setPreference(AlignParameters, true)
      .setPreference(AlignArguments, true)
      .setPreference(CompactStringConcatenation, true)
      .setPreference(SpacesAroundMultiImports, true)
      .setPreference(CompactStringConcatenation, true)
      .setPreference(CompactControlReadability, false)
      .setPreference(AlignSingleLineCaseStatements, true)
      .setPreference(AlignSingleLineCaseStatements.MaxArrowIndent, 40)
      .setPreference(SpacesWithinPatternBinders, true)
      .setPreference(DoubleIndentClassDeclaration, false)
  }

  lazy val standardSettings = Defaults.defaultSettings ++
  SbtScalariform.scalariformSettings ++
  Seq(
    version                     := appVersion,
    scalaVersion                := V.Scala,
    organization                := "com.danieltrinh",
    // This will run code in a forked JVM. Necessary to get javaOptions to run
    fork                        := true,
    ScalariformKeys.preferences := formattingPreferences,
    libraryDependencies         ++= appDependencies,
    resolvers                   ++= appResolvers,
    javaOptions in Test         ++= Seq(
      // Uncomment this line when there's problems loading .conf files
      // "-Dconfig.trace=loads", 
      "-Dconfig.resource=test.conf"),
    scalacOptions               ++= scalacSettings
  )

  lazy val sprawler: Project = Project(
    id        = appName,
    base      = file("."),
    settings  = standardSettings
  )
  .configs(Atmos)
  .settings(
    atmosSettings: _*
  )

  lazy val deadLinksDemo: Project = play.Project(
    appName + "DeadLinksDemo", 
    "0.1.0",
    appDependencies,
    path = file("examples/deadLinks")
  ).settings(
    jacoco.settings ++
    SbtScalariform.scalariformSettings ++
    Seq(ScalariformKeys.preferences := formattingPreferences) ++ 
    (scalacOptions ++= scalacSettings) ++
    Yeoman.yeomanSettings: _*
  ).dependsOn(
    sprawler
  ).aggregate(
    sprawler
  )
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
    val Scala = "2.10.3"
    val Spray = "1.2.0"
    val Akka  = "2.2.3"
    val SprayNightly = "1.2-20130912"
  }

  // NOTE: if you see a [error] java.lang.ExceptionInInitializerError
  // it is probably because of a pattern matching problem in one of the dependencies
  // below.

  // Misc
  val miscDeps = Seq(
    "com.typesafe"           %  "config"           % "1.0.2",
    "com.github.nscala-time" %% "nscala-time"      % "0.6.0",
    "com.typesafe.atmos"     %  "trace-akka-2.1.4" % "1.2.1",
    "org.scala-lang.modules" %% "scala-async"      % "0.9.0-M4"
  )
  val Seq(typesafeConfig, nscalaTime, akkaTrace, scalaAsync) = miscDeps

  // Webcrawler related dependencies
  val crawlerDeps = Seq(
    "com.google.code.crawler-commons" % "crawler-commons"  % "0.2",
    "org.jsoup"                       % "jsoup"            % "1.7.2"
  )
  val Seq(crawlerCommons, jsoup) = crawlerDeps

  // Akka
  val akkaDeps = Seq(
    "com.typesafe.akka" %% "akka-agent" % V.Akka,
    "com.typesafe.akka" %% "akka-actor" % V.Akka,
    "com.typesafe.akka" %% "akka-contrib" % V.Akka
  )
  val Seq(akkaAgent, akkaActor, akkaContrib) = akkaDeps

  // Spray
  val sprayDeps = Seq(
    "io.spray" % "spray-client"  % V.Spray,
    "io.spray" % "spray-can"     % V.Spray,
    "io.spray" % "spray-http"    % V.Spray,
    "io.spray" % "spray-httpx"   % V.Spray,
    "io.spray" % "spray-util"    % V.Spray,
    "io.spray" % "spray-caching" % V.Spray
  )
  val Seq(
    sprayClient,
    sprayCan,
    sprayHttp,
    sprayHttpx,
    sprayUtil,
    sprayCaching
  ) = sprayDeps

  // Play
  val playDeps = Seq(
    "com.typesafe.play" %% "play-json" % "2.2.1"
  )
  val Seq(playJson) = playDeps

  // Testing dependencies
  val testDeps = Seq(
    "com.typesafe.akka" %% "akka-testkit"                % V.Akka   % "test",
    "org.scalatest"     %  "scalatest_2.10"              % "2.0"    % "test",
    "org.scalamock"     %% "scalamock-scalatest-support" % "3.0.1"  % "test",
    // Used to start a bare bones server for testing the crawler
    "io.spray"          %  "spray-routing"               % V.Spray  % "test"
  )
  val Seq(akkaTestkit, scalaTest, scalaMock, sprayRouting) = testDeps
}

/**
 * Commands to run before a REPL session is loaded.
 */
object PreRun {

  val everything = Imports.imports + Commands.commands

  object Imports {
    val akka =
      """
        |import akka.actor._
        |import akka.contrib.throttle._
        |import akka.contrib.throttle.Throttler._
        |import akka.contrib.throttle.Throttler.SetTarget
        |import akka.contrib.throttle.Throttler.Rate
      """.stripMargin

    val webcrawler =
      """
        |import sprawler._
        |import sprawler.CrawlerExceptions._
        |import sprawler.XmlParser
        |import sprawler.http.client.HttpCrawlerClient
        |import sprawler.config.CrawlerConfig
        |import sprawler.config.SprayCanConfig
        |import sprawler.crawler.url._
        import crawlercommons.robots.{ BaseRobotRules, SimpleRobotRulesParser }
      """.stripMargin

    val scala =
      """
        |import scala.concurrent.{ Promise, Future }
        |import scala.async.Async.{ async, await }
        |import scala.concurrent.duration._
        |import scala.concurrent.Await
        |import scala.util.{ Try, Success, Failure }
        |import scala.{ Some, None }
      """.stripMargin

    val spray =
      """
        |import spray.client.pipelining._
        |import spray.http._
        |import spray.http.HttpResponse
        |import HttpMethods._
      """.stripMargin

    val imports = akka + webcrawler + scala + spray
  }

  object Commands {
    val commands = ""
  }

  val includes =
    """
    """.stripMargin
}