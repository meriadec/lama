// Build shared info
ThisBuild / organization := "co.ledger"
ThisBuild / scalaVersion := "2.13.3"
ThisBuild / resolvers += Resolver.sonatypeRepo("releases")
ThisBuild / scalacOptions ++= CompilerFlags.all

// Dynver custom version formatting
def versionFmt(out: sbtdynver.GitDescribeOutput): String = {
  if (out.isCleanAfterTag) out.ref.dropPrefix
  else s"${out.ref.dropPrefix}-${out.commitSuffix.sha}"
}

def fallbackVersion(d: java.util.Date): String =
  s"HEAD-${sbtdynver.DynVer timestamp d}"

ThisBuild / dynverVTagPrefix := false
ThisBuild / version := dynverGitDescribeOutput.value.mkVersion(
  versionFmt,
  fallbackVersion(dynverCurrentDate.value)
)
ThisBuild / dynver := {
  val d = new java.util.Date
  sbtdynver.DynVer.getGitDescribeOutput(d).mkVersion(versionFmt, fallbackVersion(d))
}

// Shared Plugins
enablePlugins(BuildInfoPlugin)
ThisBuild / libraryDependencies += compilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3")

lazy val ignoreFiles = List("application.conf.sample")

// Runtime
scalaVersion := "2.13.3"
scalacOptions ++= CompilerFlags.all
resolvers += Resolver.sonatypeRepo("releases")
addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3")

lazy val buildInfoSettings = Seq(
  buildInfoKeys := Seq[BuildInfoKey](version, git.gitHeadCommit),
  buildInfoPackage := "buildinfo"
)

lazy val assemblySettings = Seq(
  test in assembly := {},
  assemblyOutputPath in assembly := file(
    target.value.getAbsolutePath
  ) / "assembly" / (name.value + ".jar"),
  cleanFiles += file(target.value.getAbsolutePath) / "assembly",
  // Remove resources files from the JAR (they will be copied to an external folder)
  assemblyMergeStrategy in assembly := {
    case PathList("META-INF", _) => MergeStrategy.discard
    case PathList("BUILD")       => MergeStrategy.discard
    case path =>
      if (ignoreFiles.contains(path))
        MergeStrategy.discard
      else
        (assemblyMergeStrategy in assembly).value(path)
  }
)

lazy val dockerSettings = Seq(
  imageNames in docker := {
    // Tagging latest + dynamic version
    Seq(
      ImageName(s"docker.pkg.github.com/ledgerhq/lama/${name.value}:latest"),
      ImageName(s"docker.pkg.github.com/ledgerhq/lama/${name.value}:${version.value}")
    )
  },
  // User `docker` to build docker image
  dockerfile in docker := {
    // The assembly task generates a fat JAR file
    val artifact: File     = (assemblyOutputPath in assembly).value
    val artifactTargetPath = s"/app/${(assemblyOutputPath in assembly).value.name}"
    new Dockerfile {
      from("openjdk:14.0.2")
      copy(artifact, artifactTargetPath)
      entryPoint("java", "-jar", artifactTargetPath)
    }
  }
)

lazy val sharedSettings =
  assemblySettings ++ dockerSettings ++ Defaults.itSettings

// Common lama library
lazy val common = (project in file("common"))
  .enablePlugins(Fs2Grpc)
  .configs(IntegrationTest)
  .settings(
    name := "lama-common",
    scalapbCodeGeneratorOptions += CodeGeneratorOption.FlatPackage,
    libraryDependencies ++= (Dependencies.lamaCommon ++ Dependencies.test),
    test in assembly := {}
  )

lazy val accountManager = (project in file("account-manager"))
  .enablePlugins(Fs2Grpc, sbtdocker.DockerPlugin)
  .configs(IntegrationTest)
  .settings(
    name := "lama-account-manager",
    sharedSettings,
    // Dependencies
    libraryDependencies ++= (Dependencies.accountManager ++ Dependencies.test),
    // Proto config
    scalapbCodeGeneratorOptions += CodeGeneratorOption.FlatPackage,
    PB.protoSources in Compile += file("coins/bitcoin/common/src/main/protobuf")
  )
  .dependsOn(common)

lazy val bitcoinApi = (project in file("coins/bitcoin/api"))
  .enablePlugins(BuildInfoPlugin, Fs2Grpc, sbtdocker.DockerPlugin)
  .configs(IntegrationTest)
  .settings(
    name := "lama-bitcoin-api",
    libraryDependencies ++= (Dependencies.btcApi ++ Dependencies.test),
    sharedSettings,
    buildInfoSettings,
    // Proto config
    scalapbCodeGeneratorOptions += CodeGeneratorOption.FlatPackage,
    PB.protoSources in Compile := Seq(
      file("common/src/main/protobuf"),
      file("account-manager/src/main/protobuf"),
      file("coins/bitcoin/keychain/pb/keychain"),
      file("coins/bitcoin/common/src/main/protobuf")
    )
  )
  .dependsOn(accountManager, bitcoinCommon, common)

lazy val bitcoinCommon = (project in file("coins/bitcoin/common"))
  .enablePlugins(Fs2Grpc)
  .settings(
    name := "lama-bitcoin-common",
    libraryDependencies ++= Dependencies.btcCommon,
    // Proto config
    scalapbCodeGeneratorOptions += CodeGeneratorOption.FlatPackage,
    PB.protoSources in Compile += file("coins/bitcoin/keychain/pb/keychain")
  )
  .dependsOn(common)

lazy val bitcoinWorker = (project in file("coins/bitcoin/worker"))
  .enablePlugins(Fs2Grpc, sbtdocker.DockerPlugin)
  .configs(IntegrationTest)
  .settings(
    name := "lama-bitcoin-worker",
    sharedSettings,
    libraryDependencies ++= (Dependencies.btcWorker ++ Dependencies.test)
  )
  .dependsOn(common, bitcoinCommon)

lazy val bitcoinInterpreter = (project in file("coins/bitcoin/interpreter"))
  .enablePlugins(sbtdocker.DockerPlugin)
  .configs(IntegrationTest)
  .settings(
    name := "lama-bitcoin-interpreter",
    sharedSettings,
    libraryDependencies ++= (Dependencies.btcInterpreter ++ Dependencies.test),
    parallelExecution in IntegrationTest := false
  )
  .dependsOn(common, bitcoinCommon)

lazy val bitcoinBroadcaster = (project in file("coins/bitcoin/broadcaster"))
  .enablePlugins(Fs2Grpc, sbtdocker.DockerPlugin)
  .configs(IntegrationTest)
  .settings(
    name := "lama-bitcoin-broadcaster",
    sharedSettings,
    libraryDependencies ++= (Dependencies.btcCommon ++ Dependencies.test)
  )
  .dependsOn(common, bitcoinCommon)
