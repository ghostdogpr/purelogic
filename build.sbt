val scala3Version = "3.3.7"

inThisBuild(
  List(
    scalaVersion := scala3Version,
    organization := "com.github.ghostdogpr",
    homepage     := Some(url("https://github.com/ghostdogpr/purelogic")),
    licenses     := List(License.Apache2),
    scmInfo      := Some(ScmInfo(url("https://github.com/ghostdogpr/purelogic/"), "scm:git:git@github.com:ghostdogpr/purelogic.git")),
    developers   := List(Developer("ghostdogpr", "Pierre Ricadat", "ghostdogpr@gmail.com", url("https://github.com/ghostdogpr"))),
    resolvers += Resolver.sonatypeCentralSnapshots
  )
)

name := "purelogic"

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("check", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")

lazy val root = project
  .in(file("."))
  .settings(publish / skip := true)
  .aggregate(core.jvm, core.js)

lazy val core = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("core"))
  .settings(name := "purelogic")
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-test"     % "2.1.24" % Test,
      "dev.zio" %%% "zio-test-sbt" % "2.1.24" % Test
    )
  )
  .jsSettings(Test / fork := false)

lazy val commonSettings = Def.settings(
  scalacOptions ++= Seq(
    "-deprecation",
    "-Xfatal-warnings",
    "-no-indent",
    "-Wunused:imports,params,privates,implicits,explicits,nowarn",
    "-Wvalue-discard",
    "-Ykind-projector"
  ),
  Test / fork := true
)
