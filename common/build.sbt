name := "gitrate-common"

lazy val slf4jVersion = "1.7.21"

libraryDependencies ++= Seq(
  "cc.qbits" % "sextant" % "1.0.2" exclude("org.slf4j", "slf4j-api") exclude("org.slf4j", "slf4j-log4j12"),

  "org.slf4j" % "slf4j-api" % slf4jVersion % Test,
  "org.slf4j" % "slf4j-log4j12" % slf4jVersion % Test,
)
