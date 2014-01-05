val buildFiles = SettingKey[Map[File, Seq[Byte]]]("build-files")

buildFiles := getBuildFiles((baseDirectory in ThisBuild).value)

def getBuildFiles(base: File) =
  ((base * "*.sbt") +++ ((base / "project") ** ("*.scala" | "*.sbt"))).get.map{
    f => f -> collection.mutable.WrappedArray.make[Byte](Hash(f))
  }.toMap

shellPrompt in ThisBuild := { state =>
  val changedMessage = {
    if(getBuildFiles((baseDirectory in ThisBuild).value) != buildFiles.value)
      scala.Console.RED + "\nbuild files changed. please reload project\n\n" + scala.Console.RESET
    else ""
  }
  val branch =
    if(file(".git").exists)
      "git branch".lines_!.find{_.head == '*'}.map{_.drop(1)}.getOrElse("")
    else ""
  changedMessage + Project.extract(state).currentRef.project + branch + " " + scalaVersion.value + " > "
}

onLoadMessage := {
  println(
s"""${name.value}
scalaVersion = ${scalaVersion.value}, crossVersion = ${crossVersion.value}, binaryVersion = ${scalaBinaryVersion.value}
""")
  onLoadMessage.value
}

credentials := Nil

initialCommands in console := {
  if(libraryDependencies.value.exists(_.organization == "org.scalaz"))
    "import scalaz._,Scalaz._"
  else ""
}
