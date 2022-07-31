import sbtassembly.AssemblyPlugin.autoImport.assembly

val Name = "tcp-chat"
val Version = "1.0.0-SNAPSHOT"
val scala3Version = "3.1.3"

lazy val AkkaVersion = "2.6.19"

lazy val LicenseHeader = Some(HeaderLicense.Custom(
  """|Copyright (c) 2022 by Vadim Bondarev
     |This software is licensed under the Apache License, Version 2.0.
     |You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.
     |""".stripMargin))

lazy val scalac213Settings = Seq(
  scalacOptions ++= Seq(
    //"-deprecation",                // Emit warning and location for usages of deprecated APIs.
    "-Xsource:3",
    "-target:jvm-14",
    //"-target:jvm-14",
    "-explaintypes",                 // Explain type errors in more detail.
    "-feature",                      // Emit warning and location for usages of features that should be imported explicitly.
    "-language:existentials",        // Existential types (besides wildcard types) can be written and inferred
    "-language:experimental.macros", // Allow macro definition (besides implementation and application)
    "-language:higherKinds",         // Allow higher-kinded types
    "-language:implicitConversions", // Allow definition of implicit functions called views
    "-unchecked",                    // Enable additional warnings where generated code depends on assumptions.
    "-Xcheckinit",                   // Wrap field accessors to throw an exception on uninitialized access.
    "-Xlint:adapted-args",                       // Warn if an argument list is modified to match the receiver.
    "-Xlint:constant",                           // Evaluation of a constant arithmetic expression results in an error.
    "-Xlint:delayedinit-select",                 // Selecting member of DelayedInit.
    "-Xlint:doc-detached",                       // A Scaladoc comment appears to be detached from its element.
    "-Xlint:inaccessible",                       // Warn about inaccessible types in method signatures.
    "-Xlint:infer-any",                          // Warn when a type argument is inferred to be `Any`.
    "-Xlint:missing-interpolator",               // A string literal appears to be missing an interpolator id.
    "-Xlint:nullary-unit",                       // Warn when nullary methods return Unit.
    "-Xlint:option-implicit",                    // Option.apply used implicit view.
    "-Xlint:package-object-classes",             // Class or object defined in package object.
    "-Xlint:poly-implicit-overload",             // Parameterized overloaded implicit methods are not visible as view bounds.
    "-Xlint:private-shadow",                     // A private field (or class parameter) shadows a superclass field.
    "-Xlint:stars-align",                        // Pattern sequence wildcard must align with sequence component.
    "-Xlint:type-parameter-shadow",              // A local type parameter shadows a type already in scope.
    "-Ywarn-dead-code",                          // Warn when dead code is identified.
    "-Ywarn-extra-implicit",                     // Warn when more than one implicit parameter section is defined.
    "-Ywarn-numeric-widen",                      // Warn when numerics are widened.
    "-Ywarn-unused:implicits",                   // Warn if an implicit parameter is unused.
    "-Ywarn-unused:imports",                     // Warn if an import selector is not referenced.
    "-Ywarn-unused:locals",                      // Warn if a local definition is unused.
    "-Ywarn-unused:params",                      // Warn if a value parameter is unused.
    "-Ywarn-unused:patvars",                     // Warn if a variable bound in a pattern is unused.
    "-Ywarn-unused:privates",                    // Warn if a private member is unused.
    "-Ycache-plugin-class-loader:last-modified", // Enables caching of classloaders for compiler plugins
    "-Ycache-macro-class-loader:last-modified",  // and macro definitions. This can lead to performance improvements.
    "-Xfatal-warnings",                          // Fail the compilation if there are any warnings.
  )
)

lazy val scalac3Settings = Seq(
  scalacOptions ++= Seq(
    "-deprecation",
    "-feature",
    "-language:implicitConversions",
    "-unchecked",
    //"-Yexplicit-nulls",
    //"-Wunused",
    "-Ykind-projector",
    "-Ysafe-init", //guards against forward access reference
    "-Xfatal-warnings",
  ) ++ Seq("-rewrite", "-indent") ++ Seq("-source", "future")
)

lazy val commonSettings = scalac3Settings ++ Seq(
  name := Name,
  organization := "com.demo",

  version := Version,
  startYear := Some(2022),

  Test / parallelExecution := false,
  run / fork := false,

  assembly / assemblyJarName  := Name + "-" + Version + ".jar",

  assembly / mainClass := Some("server.Main"),

  Compile / console / scalacOptions --= Seq("-Wunused:_", "-Xfatal-warnings"),

  //sbt headerCreate
  licenses += ("Apache-2.0", new URL("https://www.apache.org/licenses/LICENSE-2.0.txt")),
  scalaVersion := scala3Version,

  headerMappings := headerMappings.value + (HeaderFileType.scala -> HeaderCommentStyle.cppStyleLineComment),
  headerLicense  := LicenseHeader
)

lazy val root = project
  .in(file("."))
  .settings(commonSettings)
  .settings(
    name := Name,
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    libraryDependencies ++= Seq(
      "ch.qos.logback"    % "logback-classic"     % "1.2.11",
      "com.typesafe.akka" %% "akka-actor-typed"   % AkkaVersion withSources() withJavadoc(),
      "com.typesafe.akka" %% "akka-stream-typed"  % AkkaVersion withSources() withJavadoc(),
      "com.typesafe.akka" %% "akka-slf4j"         % AkkaVersion,
      "org.scodec"        %% "scodec-core"        % "2.1.0",
      "com.madgag.spongycastle" % "core" % "1.58.0.0",
      /*"org.bouncycastle" % "bcprov-jdk15on" % "1.70",
      "org.bouncycastle" % "bcpkix-jdk15on" % "1.70",*/
    ),
  )
  .aggregate(scala2).dependsOn(scala2)

lazy val scala2 = project
  .in(file("scala2"))
  .settings(scalac213Settings)
  .settings(
    name := "scala2-bridge",
    scalaVersion := "2.13.8",

    //Scala 2.13 dependencies
    libraryDependencies ++= Seq(
      "com.github.pureconfig" %% "pureconfig" % "0.17.1",
      //"com.github.alexandrnikitin" %%   "bloom-filter"  % "0.13.1",
      //"com.rklaehn" %% "radixtree" % "0.5.0"
    )
  )

scalafmtOnCompile := true

addCommandAlias("c", "compile")
addCommandAlias("r", "reload")


//comment out for test:run
run / fork := true
run / connectInput := true

//used when Compile / run / fork := true


//javaOptions ++= Seq("-XshowSettings", "-Xmx700M", "-XX:MaxMetaspaceSize=550m", "-XX:+UseG1GC")
javaOptions ++= Seq("-XX:+PrintCommandLineFlags", "-XshowSettings:vm", "-Xmx600M", "-XX:MaxMetaspaceSize=450m", "-XX:+UseG1GC")

//https://medium.com/akka-scala/scala-3-create-an-sbt-project-with-subprojects-and-build-the-fat-jar-1b992643eb59
//assembly
//java -jar /Users/vadimbondarev/projects/scala3-multiproject-template/target/scala-3.1.1/orchestrator-1.0.0-SNAPSHOT.jar