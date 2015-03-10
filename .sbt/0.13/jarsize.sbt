val jarSize = TaskKey[Long]("jarSize")
 
jarSize := {
  import sbinary.DefaultProtocol._
  val s = streams.value.log
  (packageBin in Compile).?.value.map{ jar =>
    val current = jar.length
    val id = thisProject.value.id
    val currentSize = s"[$id] current $current"
    jarSize.previous match {
      case Some(previous) =>
        s.info(s"$currentSize, previous $previous, diff ${current - previous}")
      case None =>
        s.info(currentSize)
    }
    current
  }.getOrElse(-1)
}
