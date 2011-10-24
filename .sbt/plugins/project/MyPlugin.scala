import sbt._
import sbt.Keys._
import sbt.complete.Parser
import sbt.complete.DefaultParsers._

object MyPlugin extends sbt.Plugin {

  object MyPluginKeys{

    lazy val gitCommandParser = {
      (  Space ~> ( 
         token("add") | "branch" | "push" | "init" | "status" |
         "tag" | "log" | "checkout" | "rm" | "diff" | "mv" )
      ) ~ (
        ( Space ?) ~> ( any *)
      )
    }

  }

  import MyPluginKeys._

  lazy val myPluginSettings = Defaults.defaultSettings ++ Seq(
    sbtPlugin := true,
    commands ++= Seq(
      Command("git")(_ => gitCommandParser) {case (state, ( cmd , params ) ) =>
        Seq("git",cmd,params.mkString).mkString(" ") ! ;
        state
      },
      Command.args("sh", "<shell command>") { (state, args) => 
        val ret = args.mkString(" ") !;
        println("\nreturn code " + ret)
        state
      }
    )
  )
}

