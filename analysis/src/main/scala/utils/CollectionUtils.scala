package utils

object CollectionUtils {

  type MapOfSeq[K, V] = Map[K, Seq[V]]

  def seqOfMapsToMap[K, V](xs: Seq[MapOfSeq[K, V]]): MapOfSeq[K, V] =
    xs.foldLeft(Map[K, Seq[V]]())(mergeMaps)

  private def mergeMaps[K, V](out: MapOfSeq[K, V], x: MapOfSeq[K, V]): MapOfSeq[K, V] = x.foldLeft(out) {
    case (out, (key, value)) =>
      val oldValue: Seq[V] = out.getOrElse(key, Seq.empty)
      val newValue: Seq[V] = oldValue ++ value
      out + (key -> newValue)
  }

}
