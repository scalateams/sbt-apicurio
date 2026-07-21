package org.scalateams.sbt.apicurio

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalateams.sbt.apicurio.ApicurioModels.*

/** Unit tests for the pure stale-pin selection logic extracted from the `apicurioPull` task body. The registry lookup
  * is injected, so these run without a live registry.
  */
class ComputeStalePinsSpec extends AnyFlatSpec with Matchers {

  private val log = new TestLogger

  private def dep(artifactId: String, version: String): ApicurioDependency =
    ApicurioDependency("g", artifactId, version)

  private def versionMeta(v: String): VersionMetadata =
    VersionMetadata(
      version = v,
      groupId = "g",
      artifactId = "a",
      artifactType = "AVRO",
      contentId = 1L,
      globalId = 1L,
      state = "ENABLED",
      createdOn = "2020-01-01T00:00:00Z"
    )

  /** A lookup that always reports `latest` as the registry's latest version. */
  private def constLookup(latest: String): (String, String) => ApicurioResult[VersionMetadata] =
    (_, _) => Right(versionMeta(latest))

  /** A lookup that fails the test if it is ever invoked. */
  private val failIfCalled: (String, String) => ApicurioResult[VersionMetadata] =
    (g, a) => fail(s"lookup should not have been called for $g:$a")

  "computeStalePins" should "return nothing and make no lookups when warnOnStale is false" in {
    SchemaFileUtils.computeStalePins(Seq(dep("a", "1.0.0")), warnOnStale = false, failIfCalled, log) shouldBe empty
  }

  it should "skip 'latest' pins without a lookup (case-insensitive)" in {
    SchemaFileUtils.computeStalePins(
      Seq(dep("a", "latest"), dep("b", "LATEST")),
      warnOnStale = true,
      failIfCalled,
      log
    ) shouldBe empty
  }

  it should "report a pin strictly older than the registry latest" in {
    val d = dep("a", "1.0.0")
    SchemaFileUtils.computeStalePins(Seq(d), warnOnStale = true, constLookup("2.0.0"), log) shouldBe Seq((d, "2.0.0"))
  }

  it should "treat a pin equal to latest by semver precedence as not stale (\"3\" vs \"3.0.0\")" in {
    SchemaFileUtils.computeStalePins(Seq(dep("a", "3")), warnOnStale = true, constLookup("3.0.0"), log) shouldBe empty
  }

  it should "not report a pin newer than the registry latest" in {
    SchemaFileUtils.computeStalePins(
      Seq(dep("a", "2.1.0")),
      warnOnStale = true,
      constLookup("2.0.0"),
      log
    ) shouldBe empty
  }

  it should "ignore a pin whose registry lookup fails" in {
    val lookup: (String, String) => ApicurioResult[VersionMetadata] =
      (g, a) => Left(ApicurioError.ArtifactNotFound(g, a))
    SchemaFileUtils.computeStalePins(Seq(dep("a", "1.0.0")), warnOnStale = true, lookup, log) shouldBe empty
  }

  it should "report only the stale pins among a mix, preserving declaration order" in {
    val stale1        = dep("old1", "1.0.0")
    val equalToLatest = dep("cur", "5.0.0")
    val stale2        = dep("old2", "4.9.9")
    val newer         = dep("new", "6.0.0")
    val latestPin     = dep("any", "latest")
    val deps          = Seq(stale1, equalToLatest, stale2, newer, latestPin)
    SchemaFileUtils.computeStalePins(deps, warnOnStale = true, constLookup("5.0.0"), log) shouldBe
      Seq((stale1, "5.0.0"), (stale2, "5.0.0"))
  }
}
