val buildFiles = SettingKey[Map[File, Seq[Byte]]]("build-files")

buildFiles := getBuildFiles((baseDirectory in ThisBuild).value)

def getBuildFiles(base: File) =
  ((base * "*.sbt") +++ ((base / "project") ** ("*.scala" | "*.sbt"))).get.map{
    f => f -> collection.mutable.WrappedArray.make[Byte](Hash(f))
  }.toMap

def changed(base: File, files: Map[File, Seq[Byte]]): Boolean =
  getBuildFiles(base) != files

shellPrompt in ThisBuild := { state =>
  val branch = {
    if(file(".git").exists)
      "git branch".lines_!.find{_.head == '*'}.map{_.drop(1)}.getOrElse("")
    else ""
  }
  {
    if(changed((baseDirectory in ThisBuild).value, buildFiles.value))
      scala.Console.RED + "\nbuild files changed. please reload project\n\n" + scala.Console.RESET
    else ""
  } + Project.extract(state).currentRef.project + branch + " > "
}

onLoadMessage := {
  println(s"${name.value} ${thisProject.value.id}")
  println(s"scalaVersion = ${scalaVersion.value}, crossVersion = ${crossVersion.value}, binaryVersion = ${scalaBinaryVersion.value}")
  println()
  onLoadMessage.value
}

def containsScalaz(modules: Seq[ModuleID]) =
modules.exists(m => m.organization == "org.scalaz" && m.configurations.isEmpty)

val timeFunc = """
def time[A](a: => A): A = {
  System.gc
  val s = System.nanoTime
  val r = a
  println((System.nanoTime - s) / 1000000.0)
  r
}
"""

val timeMacro = """
object TimeMacro {
  import scala.language.experimental.macros
  import scala.reflect.macros.blackbox.Context
  def timeM[A](a: A): A = macro TimeMacro.timeImpl[A]
  def timeImpl[A](c: Context)(a: c.Tree): c.Tree = {
    import c.universe._
    val s, r = TermName(c.freshName())
    q"System.gc; val $s = System.nanoTime; val $r = $a; println((System.nanoTime - $s) / 1000000.0); $r"
  }
}
import TimeMacro.timeM
"""

initialCommands in console := {
  (initialCommands in console).value ++ timeFunc ++ (if(scalaVersion.value.startsWith("2.11")) timeMacro else "") ++ (
    if(name.value == "scalaz-core" || containsScalaz(libraryDependencies.value)){
      """import scalaz._, std.AllInstances._, std.AllFunctions._; println("\n" + BuildInfo.version + "\n")"""
    }else ""
  )
}

resolvers ++= {
  if(name.value == "ivy-console") Opts.resolver.sonatypeReleases :: Nil
  else Nil
}

scalacOptions := {
  if(name.value == "ivy-console") Seq("-deprecation", "-language:_")
  else scalacOptions.value
}

credentials += Credentials("Sonatype Nexus Repository Manager",
                           "oss.sonatype.org",
                           "xuwei-k",
                           "")

def checkPom(pom: xml.Node): List[String] = {
  List(
    "modelVersion", "groupId", "artifactId", "version",
    "packaging", "name", "description", "url", "licenses", "developers"
  ).flatMap{ tag =>
    if((pom \ tag).isEmpty) List("<" + tag + ">") else Nil
  } ::: List(("scm", "url"), ("scm", "connection")).flatMap{ case (tag1, tag2) =>
    if((pom \ tag1 \ tag2).isEmpty) List("<" + tag1 + "><" + tag2 + ">") else Nil
  }
}

val validatePom = taskKey[Boolean]("validate pom.xml")

validatePom := {
  val errors = checkPom(xml.XML.loadFile(makePom.value))
  errors.foreach{ e =>
    streams.value.log.error("missing tag " + e)
  }
  errors.isEmpty
}

val gitCommandParser = {
  import sbt.complete.Parser
  import sbt.complete.DefaultParsers._
  ( Space ~> List(
    "add", "cherry-pick", "fetch", "help", "rebase", "grep", "branch", "commit",
    "pull", "init", "status", "tag", "log", "checkout", "rm", "diff", "mv"
   ).map(token(_)).reduceLeft(_ | _)
  ) ~
  ( ( Space ?) ~> ( any *) )
}

commands += Command("git")(_ => gitCommandParser) {case (state, ( cmd , params ) ) =>
  Seq("git", cmd, params.mkString).mkString(" ") ! ;
  state
}

commands += BasicCommands.newAlias(
  "openIdea",
  s"""eval sys.process.Process("/Applications/IntelliJ IDEA 13 CE.app/Contents/MacOS/idea" :: "${(baseDirectory in LocalRootProject).value}" :: Nil).run()"""
)
