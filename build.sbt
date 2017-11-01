name := "gitrate"

val commonSettings = Seq(
  organization := "com.gitrate",
  version := "0.1",

  scalacOptions ++= Seq(
    "-deprecation",
    "-encoding", "UTF-8",
    "-feature",
    "-language:postfixOps",
    "-target:jvm-1.8",
    "-unchecked",
    "-Xcheckinit",
    "-Xexperimental",
    "-Xfuture",
    "-Xlint",
    "-Yno-adapted-args",
    "-Ywarn-adapted-args",
    "-Ywarn-dead-code",
    "-Ywarn-numeric-widen",
    "-Ywarn-unused",
    "-Ywarn-unused-import",
    "-Ywarn-value-discard",
  ),

  resolvers += "clojars" at "https://clojars.org/repo",

  libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest" % "3.1.x-6e03d4d77" % Test,
  ),

  scalastyleConfig := baseDirectory.value / ".." / "project" / "scalastyle-config.xml",
  scalastyleConfig in Test := baseDirectory.value / ".." / "project" / "scalastyle-config-test.xml",

  coverageEnabled in(Test, compile) := true,
  coverageEnabled in(Compile, compile) := false,
)

lazy val analysis = project
  .settings(commonSettings:_*)

lazy val webapp = project
  .settings(commonSettings:_*)

lazy val main = project.in(file("."))
  .aggregate(analysis, webapp)
