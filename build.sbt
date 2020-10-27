import sbt.Keys.libraryDependencies

// Build shared info
ThisBuild / organization := "co.ledger"
ThisBuild / scalaVersion := "2.13.3"
ThisBuild / resolvers += Resolver.sonatypeRepo("releases")
ThisBuild / scalacOptions ++= CompilerFlags.all

// Dynver custom version formatting to remove the dirty suffix and prefix
ThisBuild / dynverSeparator := "-"
ThisBuild / dynverVTagPrefix := false

// Shared Plugins
enablePlugins(BuildInfoPlugin)
ThisBuild / libraryDependencies += compilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3")

lazy val ignoreFiles = List("application.conf.sample")

// Runtime
scalaVersion := "2.13.3"
scalacOptions ++= CompilerFlags.all
resolvers += Resolver.sonatypeRepo("releases")
addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3")

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
    // Tagging only to the latest
    if (isSnapshot.value)
      Seq(ImageName(s"docker.pkg.github.com/ledgerhq/lama/${name.value}:latest"))
    // Tagging latest + specific version
    else
      Seq(
        ImageName(s"docker.pkg.github.com/ledgerhq/lama/${name.value}:latest"),
        ImageName(
          namespace = Some("docker.pkg.github.com/ledgerhq/lama"),
          repository = name.value,
          tag = Some(version.value)
        )
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

lazy val sharedSettings = assemblySettings ++ dockerSettings ++ Defaults.itSettings

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
  .enablePlugins(Fs2Grpc, FlywayPlugin, sbtdocker.DockerPlugin)
  .configs(IntegrationTest)
  .settings(
    name := "lama-account-manager",
    sharedSettings,
    // Dependencies
    libraryDependencies ++= (Dependencies.accountManager ++ Dependencies.test),
    // Proto config
    scalapbCodeGeneratorOptions += CodeGeneratorOption.FlatPackage,
    PB.protoSources in Compile += file("coins/bitcoin/common/src/main/protobuf"),
    // Flyway credentials to migrate sql scripts
    flywayLocations += "db/migration",
    flywayUrl := "jdbc:postgresql://localhost:5432/lama",
    flywayUser := "lama",
    flywayPassword := "serge"
  )
  .dependsOn(common)

lazy val service = (project in file("service"))
  .enablePlugins(Fs2Grpc, sbtdocker.DockerPlugin)
  .configs(IntegrationTest)
  .settings(
    name := "lama-service",
    libraryDependencies ++= (Dependencies.service ++ Dependencies.test),
    sharedSettings,
    // Proto config
    scalapbCodeGeneratorOptions += CodeGeneratorOption.FlatPackage,
    PB.protoSources in Compile := Seq(
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
    scalapbCodeGeneratorOptions += CodeGeneratorOption.FlatPackage
  )
  .dependsOn(common)

lazy val bitcoinWorker = (project in file("coins/bitcoin/worker"))
  .enablePlugins(Fs2Grpc, sbtdocker.DockerPlugin)
  .configs(IntegrationTest)
  .settings(
    name := "lama-bitcoin-worker",
    sharedSettings,
    libraryDependencies ++= (Dependencies.btcWorker ++ Dependencies.test),
    // Proto config
    scalapbCodeGeneratorOptions += CodeGeneratorOption.FlatPackage,
    PB.protoSources in Compile += file("coins/bitcoin/keychain/pb/keychain")
  )
  .dependsOn(common, bitcoinCommon)

lazy val bitcoinInterpreter = (project in file("coins/bitcoin/interpreter"))
  .enablePlugins(FlywayPlugin, sbtdocker.DockerPlugin)
  .configs(IntegrationTest)
  .settings(
    name := "lama-bitcoin-interpreter",
    sharedSettings,
    libraryDependencies ++= (Dependencies.btcInterpreter ++ Dependencies.test),
    // Flyway credentials to migrate sql scripts
    flywayLocations += "db/migration",
    flywayUrl := "jdbc:postgresql://localhost:5433/lama_btc",
    flywayUser := "lama",
    flywayPassword := "serge",
    parallelExecution in IntegrationTest := false
  )
  .dependsOn(common, bitcoinCommon)
