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
      // TODO https://github.com/sbt/sbt/issues/2480
      // scala.Console.RED + "\nbuild files changed. please reload project\n\n" + scala.Console.RESET
      "   build files changed. please reload project   "
    else ""
  } + Project.extract(state).currentRef.project + branch + " > "
}

fullResolvers ~= {_.filterNot(_.name == "jcenter")}

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

val validatePom = taskKey[Option[Boolean]]("validate pom.xml")

validatePom := makePom.?.value.map{ f =>
  val errors = checkPom(xml.XML.loadFile(f))
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

def openIdea(ideaCommandName: String, n: String) = {
  commands += BasicCommands.newAlias(
    ideaCommandName,
    s"""eval {sys.process.Process("/Applications/IntelliJ IDEA $n.app/Contents/MacOS/idea" :: "${(baseDirectory in LocalRootProject).value}" :: Nil).run(sys.process.ProcessLogger(_ => ()));()}"""
  )
}

openIdea("openIdea13", "13 CE")
 
openIdea("openIdea14", "14 CE")

openIdea("openIdea15", "15 CE")
 
openIdea("openIdea", "CE")


TaskKey[Unit]("showDoc") in Compile := {
  val _ = (doc in Compile).?.value
  val out = (target in doc in Compile).value
  java.awt.Desktop.getDesktop.open(out / "index.html")
}

pomPostProcess := { node =>
  import scala.xml._
  import scala.xml.transform._
  def stripIf(f: Node => Boolean) = new RewriteRule {
    override def transform(n: Node) =
      if (f(n)) NodeSeq.Empty else n
  }
  val stripTestScope = stripIf { n => n.label == "dependency" && (n \ "scope").text == "test" }
  new RuleTransformer(stripTestScope).transform(node)(0)
}

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

def addGlobalPlugin(moduleId: String, taskName: String): Seq[Def.Setting[_]] = {
  val removeCommand= "removeTemporary" + taskName;
  def tempPluginDotSbtFile(base: File) =
    base / "project" / ("temporary" + taskName + ".sbt");
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

addGlobalPlugin(""" "com.gilt" % "sbt-dependency-graph-sugar" % "0.7.5-1" """, "dependencySvgView")

addGlobalPlugin(""" "com.dwijnand.sbtprojectgraph" % "sbt-project-graph" % "0.1.0" """, "projectsGraphDot")

addGlobalPlugin(""" "com.timushev.sbt" % "sbt-updates" % "0.1.10" """, "dependencyUpdates")

addGlobalPlugin(""" "com.github.mpeltonen" % "sbt-idea" % "1.6.0" """, "gen-idea")
