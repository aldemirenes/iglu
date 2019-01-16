/*
* Copyright (c) 2014-2018 Snowplow Analytics Ltd. All rights reserved.
*
* This program is licensed to you under the Apache License Version 2.0,
* and you may not use this file except in compliance with the
* Apache License Version 2.0.
* You may obtain a copy of the Apache License Version 2.0 at
* http://www.apache.org/licenses/LICENSE-2.0.
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the Apache License Version 2.0 is distributed on
* an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
* express or implied.  See the Apache License Version 2.0 for the specific
* language governing permissions and limitations there under.
*/
import sbt._
import Keys._
import com.typesafe.sbt.packager.Keys.{daemonUser, maintainer}
import com.typesafe.sbt.packager.docker.{ ExecCmd, Cmd }
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport._
import com.typesafe.sbt.packager.universal.UniversalPlugin.autoImport._

object BuildSettings {
  //Basic settings for our app
  lazy val basicSettings = Seq[Setting[_]](
    organization            := "com.snowplowanalytics",
    version                 := "0.5.0-rc7",
    name                    := "iglu-server",
    description             := "Scala schema server for Iglu",
    scalaVersion            := "2.11.12",
    scalacOptions           := Seq("-deprecation", "-encoding", "utf8",
                               "-unchecked", "-feature", "-Xcheckinit", "-Ypartial-unification"),
    scalacOptions in Test   := Seq("-Yrangepos", "-deprecation"),
    maxErrors               := 5,
    // http://www.scala-sbt.org/0.13.0/docs/Detailed-Topics/Forking.html
    fork in run             := true,
    fork in Test            := true,
    // Ensure that the correct config file is loaded for testing
    javaOptions in Test     += "-Dconfig.file=./test.conf",
    shellPrompt             := { s => Project.extract(s).currentProject.id + " > " }
  )

  // Makes our SBT app settings available from within the app
  lazy val scalifySettings = Seq(sourceGenerators in Compile += task[Seq[File]] {
    val file = (sourceManaged in Compile).value / "settings.scala"
    IO.write(file, s"""
                      |package com.snowplowanalytics.iglu.server.generated
                      |object Settings {
                      |  val organization = "${organization.value}"
                      |  val version = "${version.value}"
                      |  val name = "${name.value}"
                      |  val shortName = "sr"
                      |}
                      |""".stripMargin)
    Seq(file)
  })

  lazy val dockerPgInstallCmds = Seq(
    ExecCmd("RUN", "cp", "/opt/docker/docker-entrypoint.sh", "/usr/local/bin/"),
    Cmd("RUN", "apt update"),
    Cmd("RUN", "mkdir -p /usr/share/man/man7"),
    Cmd("RUN", "apt install -y postgresql-client-9.6")
  )

  lazy val dockerSettings = Seq(
    // Use single entrypoint script for all apps
    Universal / sourceDirectory := new File(baseDirectory.value, "scripts"),
    dockerRepository := Some("snowplow-docker-registry.bintray.io"),
    dockerUsername := Some("snowplow"),
    dockerBaseImage := "snowplow-docker-registry.bintray.io/snowplow/base-debian:0.1.0",
    Docker / maintainer := "Snowplow Analytics Ltd. <support@snowplowanalytics.com>",
    Docker / daemonUser := "root",  // Will be gosu'ed by docker-entrypoint.sh
    dockerEntrypoint := Seq("docker-entrypoint.sh"),
    dockerCommands ++= dockerPgInstallCmds,
    dockerCmd := Seq("--help")
  )

  lazy val buildSettings = basicSettings ++ scalifySettings ++ dockerSettings
}
