package utils

import com.typesafe.config.{Config, ConfigFactory}

trait AppConfig {

  def appConfig: Config = appConfigValue
  def postgresqlConfig: Config = postgresqlConfigValue

  @transient private lazy val appConfigValue: Config = ConfigFactory.load("application.conf")
  @transient private lazy val postgresqlConfigValue: Config = appConfig.getConfig("db.postgresql")

}
