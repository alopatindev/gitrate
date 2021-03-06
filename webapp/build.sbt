name := "gitrate-webapp"

lazy val playSlickVersion = "3.0.1" // TODO: update to 3.0.2?
//lazy val playSlickVersion = "3.0.2"

libraryDependencies ++= Seq(
  guice,

  "com.typesafe.play" %% "play-slick" % playSlickVersion,
  "com.typesafe.play" %% "play-slick-evolutions" % playSlickVersion,
  "com.github.tminglei" %% "slick-pg" % "0.15.4",

  "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2" % Test,
)

// Adds additional packages into Twirl
//TwirlKeys.templateImports += "com.gitrate.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "com.gitrate.binders._"
