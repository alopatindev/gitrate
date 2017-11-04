package utils

object CollectionUtils {

  type MapOfSeq[K, V] = Map[K, Seq[V]]

  def seqOfMapsToMapOfSeq[K, V](xs: Seq[MapOfSeq[K, V]]): MapOfSeq[K, V] =
    xs.flatMap(_.toSeq)
      .groupBy {
        case (key, _) => key
      }
      .mapValues(_.flatMap { case (_, value) => value })

}
