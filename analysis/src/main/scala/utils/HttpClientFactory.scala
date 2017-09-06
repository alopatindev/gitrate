package hiregooddevs.utils

import java.io.InputStream
import java.net.URL

import scala.concurrent.duration._

import scalaj.http.{Http, HttpRequest}

object HttpClientFactory {

  type Headers = Map[String, String]
  type Parser[Output] = (InputStream => Output)
  type HttpPostFunction[Input, Output] = (URL, Input, Headers) => Output

  private val DefaultTimeout = 10 seconds

  def postFunction[Input, Output](parse: Parser[Output],
                                  timeout: Duration = DefaultTimeout): HttpPostFunction[Input, Output] =
    (url: URL, data: Input, headers: Headers) =>
      httpClient(url, headers, timeout)
        .postData(data.toString)
        .execute(parse)
        .body

  private def httpClient(url: URL, headers: Headers, timeout: Duration): HttpRequest = {
    val timeoutMs = timeout.toMillis.toInt

    Http(url.toString)
      .headers(headers)
      .timeout(connTimeoutMs = timeoutMs, readTimeoutMs = timeoutMs)
  }

}
