name := "hiregooddevs-analysis"

scalaVersion := "2.11.11"
scalacOptions in (Compile, console) ++= Seq(
  "-feature",
  "-deprecation",
  "-encoding", "UTF-8",
  "-unchecked",
  "-Xlint",
  "-Yno-adapted-args",
  "-Ywarn-dead-code",
  "-Ywarn-value-discard",
  "-Xfuture",
  "-Xexperimental",
  "-language:postfixOps",
)

scalacOptions in (Test, console) --= Seq(
  "-feature",
)

val sparkVersion = "2.1.1"
val playWsStandaloneVersion = "1.0.4"

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-ws-standalone-json" % playWsStandaloneVersion,

  //"io.reactivex" %% "rxscala" % "0.26.5",

  "org.apache.spark" %% "spark-core" % sparkVersion,
  "org.apache.spark" %% "spark-sql" % sparkVersion,
  "org.apache.spark" %% "spark-streaming" % sparkVersion,

  //"com.typesafe.play" %% "play-json" % "2.6.2",
  //"com.datastax.spark" %% "spark-cassandra-connector" % "2.0.2",

  "org.scalatest" %% "scalatest" % "3.0.3" % Test,
  "org.scalacheck" %% "scalacheck" % "1.13.5" % Test,
  "junit" % "junit" % "4.12" % Test,
)

dependencyOverrides ++= Seq(
  "com.fasterxml.jackson.core" % "jackson-core" % "2.8.7",
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.8.7",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.8.7",
)

assemblyMergeStrategy in assembly := {
 case PathList("META-INF", xs @ _*) => MergeStrategy.discard
 case x => MergeStrategy.first
}

//testOptions in Test += Tests.Argument("-oF")
//parallelExecution in Test := false
