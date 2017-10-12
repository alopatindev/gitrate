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
