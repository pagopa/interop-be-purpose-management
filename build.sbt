import ProjectSettings.ProjectFrom
import com.typesafe.sbt.packager.docker.Cmd

ThisBuild / scalaVersion      := "2.13.10"
ThisBuild / organization      := "it.pagopa"
ThisBuild / organizationName  := "Pagopa S.p.A."
ThisBuild / dependencyOverrides ++= Dependencies.Jars.overrides
ThisBuild / version           := ComputeVersion.version
Global / onChangedBuildSource := ReloadOnSourceChanges



lazy val generateCode = taskKey[Unit]("A task for generating the code starting from the swagger definition")

val packagePrefix = settingKey[String]("The package prefix derived from the uservice name")

packagePrefix := name.value
  .replaceFirst("interop-", "interop.")
  .replaceFirst("be-", "")
  .replaceAll("-", "")

val projectName = settingKey[String]("The project name prefix derived from the uservice name")

projectName := name.value
  .replaceFirst("interop-", "")
  .replaceFirst("be-", "")

generateCode := {
  import sys.process._

  Process(s"""openapi-generator-cli generate -t template/scala-akka-http-server
             |                               -i src/main/resources/interface-specification.yml
             |                               -g scala-akka-http-server
             |                               -p projectName=${projectName.value}
             |                               -p invokerPackage=it.pagopa.${packagePrefix.value}.server
             |                               -p modelPackage=it.pagopa.${packagePrefix.value}.model
             |                               -p apiPackage=it.pagopa.${packagePrefix.value}.api
             |                               -p dateLibrary=java8
             |                               -p entityStrictnessTimeout=15
             |                               -o generated""".stripMargin).!!

  Process(s"""openapi-generator-cli generate -t template/scala-akka-http-client
             |                               -i src/main/resources/interface-specification.yml
             |                               -g scala-akka
             |                               -p projectName=${projectName.value}
             |                               -p invokerPackage=it.pagopa.${packagePrefix.value}.client.invoker
             |                               -p modelPackage=it.pagopa.${packagePrefix.value}.client.model
             |                               -p apiPackage=it.pagopa.${packagePrefix.value}.client.api
             |                               -p dateLibrary=java8
             |                               -o client""".stripMargin).!!

}

(Compile / compile) := ((Compile / compile) dependsOn generateCode).value
(Test / test)       := ((Test / test) dependsOn generateCode).value

Compile / PB.targets := Seq(scalapb.gen() -> (Compile / sourceManaged).value / "protobuf")

cleanFiles += baseDirectory.value / "generated" / "src"

cleanFiles += baseDirectory.value / "generated" / "target"

cleanFiles += baseDirectory.value / "client" / "src"

cleanFiles += baseDirectory.value / "client" / "target"

val runStandalone = inputKey[Unit]("Run the app using standalone configuration")
runStandalone := {
  task(System.setProperty("config.file", "src/main/resources/application-standalone.conf")).value
  (Compile / run).evaluated
}

lazy val generated = project
  .configs(IntegrationTest)
  .settings(Defaults.itSettings: _*)
  .in(file("generated"))
  .settings(
    scalacOptions       := Seq(),
    libraryDependencies := Dependencies.Jars.generated,
    scalafmtOnCompile   := true,
    publish / skip      := true
  )
  .setupBuildInfo

lazy val models = project
  .in(file("models"))
  .settings(
    name                := "interop-be-purpose-management-models",
    libraryDependencies := Dependencies.Jars.models,
    scalafmtOnCompile   := true,
    Docker / publish    := {}
  )

lazy val client = project
  .in(file("client"))
  .settings(
    name                := "interop-be-purpose-management-client",
    scalacOptions       := Seq(),
    scalafmtOnCompile   := true,
    libraryDependencies := Dependencies.Jars.client,
    updateOptions       := updateOptions.value.withGigahorse(false),
    Docker / publish    := {}
  )

lazy val root = (project in file("."))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings: _*)
  .settings(
    name                        := "interop-be-purpose-management",
    Test / parallelExecution    := false,
    Test / fork                 := true,
    Test / javaOptions += "-Dconfig.file=src/test/resources/application-test.conf",
    IntegrationTest / fork      := true,
    IntegrationTest / javaOptions += "-Dconfig.file=src/it/resources/application-it.conf",
    scalafmtOnCompile           := true,
    libraryDependencies         := Dependencies.Jars.`server`,
    dockerBuildOptions ++= Seq("--network=host"),
    dockerRepository            := Some(System.getenv("ECR_REGISTRY")),
    dockerBaseImage             := "adoptopenjdk:11-jdk-hotspot", ThisBuild / githubOwner := "pagopa"
      ThisBuild / githubRepository :=
"interop-be-purpose-management"
ThisBuild / resolvers += Resolver.githubPackages("pagopa")
    daemonUser                  := "daemon",
    Docker / version            := (ThisBuild / version).value.replace("-SNAPSHOT", "-latest").toLowerCase,
    Docker / packageName        := s"${name.value}",
    Docker / dockerExposedPorts := Seq(8080),
    Docker / maintainer         := "https://pagopa.it",
    dockerCommands += Cmd("LABEL", s"org.opencontainers.image.source https://github.com/pagopa/${name.value}"),
    publish / skip              := true
  )
  .aggregate(client, models)
  .dependsOn(generated, models)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  .enablePlugins(NoPublishPlugin)
  .setupBuildInfo
