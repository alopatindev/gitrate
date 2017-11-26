name := "gitrate-analysis"

lazy val sparkVersion = "2.2.0"

libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-core" % sparkVersion,
  "org.apache.spark" %% "spark-sql" % sparkVersion,
  "org.apache.spark" %% "spark-streaming" % sparkVersion,

  "org.postgresql" % "postgresql" % "42.1.4",
  "com.typesafe.slick" %% "slick" % "3.2.1",

  "com.typesafe.play" %% "play-ws-standalone-json" % "1.0.4",

  "org.scalaj" %% "scalaj-http" % "2.3.0",

  "com.typesafe" % "config" % "1.3.1",

  "com.holdenkarau" %% "spark-testing-base" % "2.2.0_0.7.4" % Test,
  "org.apache.spark" %% "spark-hive" % sparkVersion % Test // https://github.com/holdenk/spark-testing-base/issues/148
)

dependencyOverrides ++= Seq(
  // https://stackoverflow.com/questions/43841091/spark2-1-0-incompatible-jackson-versions-2-7-6/43845063#43845063
  "com.fasterxml.jackson.core" % "jackson-core" % "2.8.7",
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.8.7",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.8.7",
)

// https://stackoverflow.com/questions/25144484/sbt-assembly-deduplication-found-error/39058507#39058507
assemblyMergeStrategy in assembly := {
  case PathList("META-INF", _ @ _*) => MergeStrategy.discard
  case _: String => MergeStrategy.first
}

//testOptions in Test += Tests.Argument("-oF")

fork in run := true
fork in Test := true
cancelable in Global := true

lazy val Slow = config("slow").extend(Test)
configs(Slow)
inConfig(Slow)(Defaults.testTasks)
testOptions in Test += Tests.Argument("-l", "org.scalatest.tags.Slow")
testOptions in Slow -= Tests.Argument("-l", "org.scalatest.tags.Slow")
testOptions in Slow += Tests.Argument("-n", "org.scalatest.tags.Slow")

outputStrategy := Some(StdoutOutput)
