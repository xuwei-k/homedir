val removeSbtUpdateDotSbt = "remove-sbt-update-dot-sbt"
def sbtUpdateDotSbt(base: File) = base / "project" / "sbt-update.sbt"
 
TaskKey[Unit](removeSbtUpdateDotSbt) := {
  val f = sbtUpdateDotSbt((baseDirectory in LocalRootProject).value)
  IO.delete(f)
}
 
commands += Command.command("dependencyUpdatesPlugin"){ state =>
  val extracted = Project.extract(state)
  val f = sbtUpdateDotSbt(extracted.get(baseDirectory in LocalRootProject))
  IO.write(f, """ addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.1.6") """)
  "reload" :: "dependencyUpdates" :: removeSbtUpdateDotSbt :: "reload" :: state
}
