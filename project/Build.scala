import sbt._
import Keys._
import PlayProject._
import scalariform.formatter.preferences._
import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys

object ApplicationBuild extends Build {

  import Dependencies._
  import Resolvers._

  val appName         = "webcrawler"
  val appVersion      = "1.0"

  val appDependencies = Seq(
    akkaActor,
    akkaAgent,
    scalaTest,
    akkaTestkit,
    typesafeConfig
  ) ++ sprayDeps

  val appResolvers = Seq(
    jboss,
    scalaTools,
    typesafe,
    spray
  )

  val main = play.Project(
    appName, appVersion, appDependencies
  ).settings(
    SbtScalariform.scalariformSettings ++ 
    (resolvers ++= appResolvers) ++
    (ScalariformKeys.preferences := formattingPreferences): _*     
  )

  val formattingPreferences = {
    import scalariform.formatter.preferences._
    FormattingPreferences()
      .setPreference(DoubleIndentClassDeclaration, false)
      .setPreference(PreserveDanglingCloseParenthesis, false)
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
}

object Dependencies {

  val typesafeConfig = "com.typesafe"      %  "config"         % "1.0.0"

  // Akka
  val akkaAgent = "com.typesafe.akka" %% "akka-agent" % "2.1.2"
  val akkaActor = "com.typesafe.akka" %% "akka-actor" % "2.1.2"
  
  val akkaDeps = Seq(
    akkaAgent, akkaActor
  )
  
  // Spray
  val sprayClient = "io.spray" %  "spray-client" % "1.1-M7"
  val sprayCan    = "io.spray" %  "spray-can"    % "1.1-M7"      
  val sprayHttp   = "io.spray" %  "spray-http"   % "1.1-M7"
  val sprayHttpx  = "io.spray" %  "spray-httpx"  % "1.1-M7"
  val sprayUtil   = "io.spray" %  "spray-util"   % "1.1-M7"

  val sprayDeps = Seq(
    sprayClient,
    sprayCan,
    sprayHttp,
    sprayHttpx,
    sprayUtil
  )

  // Testing dependencies
  val akkaTestkit = "com.typesafe.akka" %% "akka-testkit"                % "2.1.2" % "test"
  val scalaTest   = "org.scalatest"     %  "scalatest_2.10"              % "1.9.1" % "test"
  val scalaMock   = "org.scalamock"     %% "scalamock-scalatest-support" % "3.0.1" % "test"
}