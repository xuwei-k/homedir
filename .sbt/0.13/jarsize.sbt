val jarSize = TaskKey[Long]("jarSize")
 
jarSize := {
  import sbinary.DefaultProtocol._
  val s = streams.value.log
  val current = (packageBin in Compile).value.length
  s.info("current  " + current)
  jarSize.previous.foreach{ previous =>
    s.info("previous " + previous)
    s.info((current - previous).toString)
  }
  current
}
