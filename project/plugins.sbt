// Publishing and versioning
addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.12.0")

// Code quality
// sbt-scalafmt 2.5.4 does not publish an sbt2/Scala-3 cross-build artifact
// (sbt-scalafmt_sbt2_3) and fails to resolve under sbt 2.0.3. Formatting is
// done via the standalone CLI instead:
//   cs launch scalafmt:3.7.17 -- --config .scalafmt.conf $(git ls-files '*.scala' '*.sbt')
// See .superpowers/sdd/task-1-report.md for details.
