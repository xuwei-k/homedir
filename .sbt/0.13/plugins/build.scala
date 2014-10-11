import sbt._, Keys._

object GlobalPluginUtil {

  def add(moduleId: String, taskName: String): Seq[Def.Setting[_]] = {
    val removeCommand= "removeTemporary" + taskName

    def tempPluginDotSbtFile(base: File) =
      base / "project" / ("temporary" + taskName + ".sbt")

    Seq(
      TaskKey[Unit](removeCommand) := {
        val f = tempPluginDotSbtFile((baseDirectory in LocalRootProject).value)
        IO.delete(f)
      },
      commands += Command.command(taskName + "Plugin"){ state =>
        val extracted = Project.extract(state)
        val f = tempPluginDotSbtFile(extracted.get(baseDirectory in LocalRootProject))
        IO.write(f, "addSbtPlugin(" + moduleId + ")")
        "reload" :: taskName :: removeCommand :: "reload" :: state
      }
    )
  }
}

