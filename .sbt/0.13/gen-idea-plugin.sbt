val removeIdeaDotSbt = "remove-idea-dot-sbt"
 
TaskKey[Unit](removeIdeaDotSbt) := {
  val f = baseDirectory.value / "project" / "idea.sbt"
  assert(f.isFile)
  IO.delete(f)
}
 
commands += Command.command("gen-idea-plugin"){ state =>
  val extracted = Project.extract(state)
  val f = extracted.get(baseDirectory) / "project" / "idea.sbt"
  IO.write(f, """ addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.6.0") """)
  "reload" :: "gen-idea" :: removeIdeaDotSbt :: "reload" :: state
}

commands += BasicCommands.newAlias(
  "openIdea",
  s"""eval {sys.process.Process("/Applications/IntelliJ IDEA 13 CE.app/Contents/MacOS/idea" :: "${(baseDirectory in LocalRootProject).value}" :: Nil).run(sys.process.ProcessLogger(_ => ()));()}"""
)
