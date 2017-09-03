package hiregooddevs.utils

import java.io.{File, InputStream}
import java.net.{HttpURLConnection, URL}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.Future

trait HttpClient {

  // FIXME: too many arguments
  def httpPostBlocking(url: String, data: String, headers: Map[String, String], timeout: Duration): InputStream = {
    val connection =
      new URL(url)
        .openConnection()
        .asInstanceOf[HttpURLConnection] // FIXME: replace with wrapper? scalaj-http?

    // TODO: retries
    connection.setConnectTimeout(timeout.toMillis.toInt)
    connection.setRequestMethod("POST")

    for ((key, value) <- headers) { // scalastyle:ignore
      connection.setRequestProperty(key, value)
    }

    connection.setDoOutput(true)
    val outputStream = connection.getOutputStream
    outputStream.write(data.getBytes("UTF-8"))
    outputStream.close()

    connection.connect()
    connection.getInputStream()
  }

}
