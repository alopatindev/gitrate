package gitrate.analysis.github

import org.apache.spark.streaming.StreamingContext
import org.apache.spark.streaming.dstream.ReceiverInputDStream
import org.apache.spark.streaming.receiver.Receiver

class GithubSearchInputDStream(ssc: StreamingContext,
                               conf: GithubConf,
                               onLoadQueries: () => Seq[GithubSearchQuery],
                               onStoreResult: (GithubReceiver, String) => Unit)
    extends ReceiverInputDStream[String](ssc) {

  override def getReceiver(): Receiver[String] = new GithubReceiver(conf, onLoadQueries, onStoreResult)

}

object GithubSearchInputDStream {

  def createStream(ssc: StreamingContext,
                   conf: GithubConf,
                   onLoadQueries: () => Seq[GithubSearchQuery],
                   onStoreResult: (GithubReceiver, String) => Unit): GithubSearchInputDStream =
    new GithubSearchInputDStream(ssc, conf, onLoadQueries, onStoreResult)

}
