
lazy val akkaHttpVersion = "10.1.6"
lazy val akkaVersion     = "2.5.19"
lazy val oauth2Version   = "0.1.8"

lazy val root = (project in file("."))
  .settings(
    inThisBuild(
      List(
        organization := "fun.zhongl",
        scalaVersion := "2.12.8"
      )),
    name := "passport",
    version := "0.0.1",
    scalacOptions += "-deprecation",
    resolvers += "jitpack" at "https://jitpack.io",
    mainClass in Compile := Some("zhongl.passport.Main"),
    maintainer in Docker := "zhong.lunfu@gmail.com",
    dockerBaseImage := "openjdk:8-alpine",
    dockerEnvVars := Map("DOCKER_HOST" -> "unix:///var/run/docker.sock"),
    dockerExposedPorts := Seq(8080),
    daemonUserUid in Docker := None,
    daemonUser in Docker := "root",
    dockerRepository := sys.props.get("docker.repository"),
    version in Docker := sys.props.get("docker.tag").getOrElse(version.value),
    libraryDependencies ++= Seq(
      "com.lightbend.akka"                   %% "akka-stream-alpakka-unix-domain-socket" % "1.0-M2",
      "com.github.scopt"                     %% "scopt"                                  % "4.0.0-RC2",
      "com.github.zhongl.akka-stream-oauth2" %% "dingtalk"                               % oauth2Version,
      "com.github.zhongl.akka-stream-oauth2" %% "wechat"                                 % oauth2Version,
      "com.typesafe.akka"                    %% "akka-http-testkit"                      % akkaHttpVersion % Test,
      "com.typesafe.akka"                    %% "akka-testkit"                           % akkaVersion % Test,
      "com.typesafe.akka"                    %% "akka-stream-testkit"                    % akkaVersion % Test,
      "org.scalatest"                        %% "scalatest"                              % "3.0.4" % Test,
      "org.scalamock"                        %% "scalamock"                              % "4.1.0" % Test
    )
  )
  .enablePlugins(JavaAppPackaging, AshScriptPlugin, DockerSpotifyClientPlugin)
