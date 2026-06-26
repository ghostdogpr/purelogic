val scala3Version = "3.3.8"
val scalaVersions = Seq(scala3Version)

// dependencies for tests and benchmarks
val catsVersion       = "2.13.0"
val catsMtlVersion    = "1.7.0"
val zioPreludeVersion = "1.0.0-RC47"
val kyoVersion        = "1.0.0-RC4"
val turboliftVersion  = "0.126.0"
val zioVersion        = "2.1.24"
val munitVersion      = "1.3.3"

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

addCommandAlias("fmt", "all scalafmtSbt scalafmt Test/scalafmt")
addCommandAlias("check", "all scalafmtSbtCheck scalafmtCheck Test/scalafmtCheck")

lazy val root = project
  .in(file("."))
  .settings(publish / skip := true)
  .aggregate(core.projectRefs *)
  .aggregate(examples, benchmarks)

lazy val core = (projectMatrix in file("core"))
  .settings(name := "purelogic")
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % munitVersion % Test
    )
  )
  .jvmPlatform(scalaVersions)
  .jsPlatform(scalaVersions, Seq(Test / fork := false))
  .nativePlatform(scalaVersions, Seq(Test / fork := false, bspEnabled := false))

lazy val coreJVM = core.jvm(scala3Version)

lazy val examples = project
  .in(file("examples"))
  .settings(name := "purelogic-examples")
  .settings(commonSettings)
  .settings(publish / skip := true)
  .settings(run / fork := true)
  .dependsOn(coreJVM)

lazy val benchmarks = project
  .in(file("benchmarks"))
  .enablePlugins(JmhPlugin)
  .settings(name := "purelogic-benchmarks")
  .settings(commonSettings)
  .settings(scalaVersion := "3.8.4")
  .settings(
    scalacOptions := scalacOptions.value.filterNot(_ == "-Ykind-projector").filterNot(_ == "-Xfatal-warnings") :+ "-Xkind-projector"
  )
  .settings(publish / skip := true)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio"            %% "zio-prelude"    % zioPreludeVersion,
      "org.typelevel"      %% "cats-core"      % catsVersion,
      "org.typelevel"      %% "cats-mtl"       % catsMtlVersion,
      "io.getkyo"          %% "kyo-core"       % kyoVersion,
      "io.github.marcinzh" %% "turbolift-core" % turboliftVersion
    )
  )
  .dependsOn(coreJVM)

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
