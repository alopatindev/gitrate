name := "gitrate-common"

libraryDependencies ++= Seq(
  "cc.qbits" % "sextant" % "1.0.2" exclude("org.slf4j", "slf4j-api") exclude("org.slf4j", "slf4j-log4j12"),
)
