scalaVersion := "2.12.3"

lazy val utils = project in file(".")

lazy val utilsBench = (project in file("bench")).enablePlugins(JmhPlugin).dependsOn(utils)

resolvers ++= Seq(
                "BioEmerg releases" at "http://bioemergences.eu/nexus/content/repositories/releases/",
                "BioEmerg snapshots" at "http://bioemergences.eu/nexus/content/repositories/snapshots/",
                "BioEmerg 3rd Party" at "http://bioemergences.eu/nexus/content/repositories/thirdparty/")

                
libraryDependencies += "org.irods.jargon" % "jargon-core" % "4.0.2.4-RELEASE"

libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3"

libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.1" % "test"

libraryDependencies ++= Seq(
	"com.typesafe.akka" %% "akka-actor" % "2.5.3",
	"com.typesafe.akka" %% "akka-testkit" % "2.5.3" % Test
)

fork in run := true

javaOptions in run += "-XX:+UseG1GC"

logBuffered in Test := false

testOptions in Test += Tests.Argument("-oDF")

name := "bioemergences-utils"

organization := "eu.bioemergences"

version := "0.5-SNAPSHOT"

crossScalaVersions := Seq("2.11.8","2.12.2")


publishTo := {
	val nexus = "http://bioemergences.eu/nexus/content/repositories/"
	if (isSnapshot.value)
		Some("snapshots" at nexus + "snapshots")
	else
		Some("releases"  at nexus + "releases")
}

credentials += Credentials(Path.userHome / ".sbt" / ".credentials")

parallelExecution in Test := false
