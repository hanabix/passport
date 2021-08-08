lazy val akkaHttpVersion = "10.2.6"
lazy val akkaVersion     = "2.6.15"
lazy val oauth2Version   = "0.1.14"

ThisBuild / dynverVTagPrefix := false

lazy val root = (project in file("."))
  .settings(
    organization := "fun.zhongl",
    scalaVersion := "2.13.4",
    name := "passport",
    scalafmtOnCompile := true,
    scalacOptions += "-deprecation",
    Compile / mainClass := Some("zhongl.passport.Main"),
    Docker / maintainer:= "zhong.lunfu@gmail.com",
    dockerBaseImage := "openjdk:8-alpine",
    dockerEnvVars := Map("DOCKER_HOST" -> "unix:///var/run/docker.sock"),
    dockerExposedPorts := Seq(8080),
    Docker / daemonUserUid := None,
    Docker / daemonUser := "root",
    dockerUsername := Some("zhongl"),
    dockerUpdateLatest := true,
    libraryDependencies ++= Seq(
      "com.github.zhongl" %% "akka-stream-netty-all"       % "0.1.10",
      "com.github.scopt"  %% "scopt"                       % "4.0.1",
      "com.github.zhongl" %% "akka-stream-oauth2-dingtalk" % oauth2Version,
      "com.github.zhongl" %% "akka-stream-oauth2-wechat"   % oauth2Version,
      "com.typesafe.akka" %% "akka-http-testkit"           % akkaHttpVersion % Test,
      "com.typesafe.akka" %% "akka-testkit"                % akkaVersion     % Test,
      "com.typesafe.akka" %% "akka-stream-testkit"         % akkaVersion     % Test,
      "org.scalatest"     %% "scalatest"                   % "3.2.9"         % Test,
      "org.scalamock"     %% "scalamock"                   % "5.1.0"         % Test
    )
  )
  .enablePlugins(JavaAppPackaging, AshScriptPlugin)
