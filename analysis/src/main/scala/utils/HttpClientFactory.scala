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

import java.io.InputStream
import java.net.URL
import scala.concurrent.blocking
import scala.concurrent.duration._
import scalaj.http.{Http, HttpRequest}

object HttpClientFactory {

  type Headers = Map[String, String]
  type Parser[Output] = (InputStream => Output)
  type HttpGetFunction[Output] = (URL, Headers) => Output
  type HttpPostFunction[Input, Output] = (URL, Input, Headers) => Output

  val defaultTimeout: FiniteDuration = 1 minute

  def getFunction[Output](parse: Parser[Output], timeout: Duration = defaultTimeout): HttpGetFunction[Output] =
    (url: URL, headers: Headers) =>
      blocking {
        httpClient(url, headers, timeout)
          .execute(parse)
          .body
    }

  def postFunction[Input, Output](parse: Parser[Output],
                                  timeout: Duration = defaultTimeout): HttpPostFunction[Input, Output] =
    (url: URL, data: Input, headers: Headers) =>
      blocking {
        httpClient(url, headers, timeout)
          .postData(data.toString)
          .execute(parse)
          .body
    }

  private def httpClient(url: URL, headers: Headers, timeout: Duration): HttpRequest = {
    val timeoutMs = timeout.toMillis.toInt

    Http(url.toString)
      .headers(headers)
      .timeout(connTimeoutMs = timeoutMs, readTimeoutMs = timeoutMs)
  }

}
