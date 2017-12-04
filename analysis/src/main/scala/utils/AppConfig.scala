// Copyright 2017 Alexander Lopatin
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package utils

import com.typesafe.config.{Config, ConfigFactory}

trait AppConfig {

  def appConfig: Config = appConfigValue
  def postgresqlConfig: Config = postgresqlConfigValue

  @transient private lazy val appConfigValue: Config = ConfigFactory.load("application.conf")
  @transient private lazy val postgresqlConfigValue: Config = appConfig.getConfig("db.postgresql")

}
