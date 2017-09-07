package gitrate.utils

import org.apache.commons.lang.text.StrSubstitutor

object StringUtils {

  implicit class Format(val s: String) {

    def formatTemplate(args: Map[String, String]): String = {
      import scala.collection.JavaConverters._
      val sub = new StrSubstitutor(args.asJava)
      sub.replace(s)
    }

  }

}
