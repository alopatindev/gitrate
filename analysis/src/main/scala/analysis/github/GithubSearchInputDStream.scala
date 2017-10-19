package analysis.github

import controllers.GithubController.GithubSearchQuery
import org.apache.spark.streaming.StreamingContext
import org.apache.spark.streaming.dstream.ReceiverInputDStream
import org.apache.spark.streaming.receiver.Receiver

class GithubSearchInputDStream(ssc: StreamingContext,
                               conf: GithubConf,
                               loadQueriesFn: () => Seq[GithubSearchQuery],
                               storeResultFn: (GithubReceiver, String) => Unit)
    extends ReceiverInputDStream[String](ssc) {

  override def getReceiver(): Receiver[String] = new GithubReceiver(conf, loadQueriesFn, storeResultFn)

}
