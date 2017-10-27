package analysis.github

import controllers.GithubController.GithubSearchQuery

import org.apache.spark.streaming.dstream.ReceiverInputDStream
import org.apache.spark.streaming.receiver.Receiver
import org.apache.spark.streaming.StreamingContext

class GithubSearchInputDStream(ssc: StreamingContext,
                               conf: GithubConf,
                               loadQueries: () => (Seq[GithubSearchQuery], Int),
                               saveReceiverState: (Int) => Unit,
                               storeResult: (GithubReceiver, String) => Unit)
    extends ReceiverInputDStream[String](ssc) {

  override def getReceiver(): Receiver[String] =
    new GithubReceiver(conf, loadQueries, saveReceiverState, storeResult)

}
