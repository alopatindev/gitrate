name := "hiregooddevs-analysis"

scalaVersion := "2.11.11"
scalacOptions ++= Seq(
  "-feature",
  "-language:postfixOps",
  "-deprecation",
  "-encoding", "UTF-8",
  "-unchecked",
  "-Xlint",
  "-Yno-adapted-args",
  "-Ywarn-dead-code",
  "-Ywarn-value-discard",
  "-Xfuture",
  "-Xexperimental",
)

val sparkVersion = "2.2.0"
val playWsStandaloneVersion = "1.0.4"

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-ahc-ws-standalone" % playWsStandaloneVersion,
  "com.typesafe.play" %% "play-ws-standalone-json" % playWsStandaloneVersion,

  //"io.reactivex" %% "rxscala" % "0.26.5",

  "org.apache.spark" %% "spark-core" % sparkVersion,
  "org.apache.spark" %% "spark-sql" % sparkVersion,
  "org.apache.spark" %% "spark-streaming" % sparkVersion,

  //"com.typesafe.play" %% "play-json" % "2.6.2",
  //"com.datastax.spark" %% "spark-cassandra-connector" % "2.0.2",
  //"org.scalacheck" %% "scalacheck" % "1.12.1" % Test,
  //"junit" % "junit" % "4.10" % Test,
)

//testOptions in Test += Tests.Argument("-oF")
//parallelExecution in Test := false
