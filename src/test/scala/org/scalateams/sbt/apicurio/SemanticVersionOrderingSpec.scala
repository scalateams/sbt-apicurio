package org.scalateams.sbt.apicurio

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SemanticVersionOrderingSpec extends AnyFlatSpec with Matchers {

  private val ordering = SemanticVersionOrdering

  // Picks the latest version the same way getLatestVersion does.
  private def latest(versions: String*): String = versions.max(ordering)

  "SemanticVersionOrdering" should "order by numeric precedence rather than lexicographically" in {
    // The lexicographic bug this replaces would pick "9.0.0" and "3.2.0".
    ordering.compare("10.0.0", "9.0.0") should be > 0
    ordering.compare("3.10.0", "3.2.0") should be > 0
    latest("3.0.0", "4.0.0", "10.0.0") shouldBe "10.0.0"
  }

  it should "treat a bare or partial core as zero-padded" in {
    ordering.compare("3", "3.0.0") shouldBe 0
    ordering.compare("3.0", "3.0.0") shouldBe 0
    ordering.compare("4", "3.9.9") should be > 0
  }

  it should "rank a release above a pre-release of the same core" in {
    ordering.compare("1.0.0", "1.0.0-rc.1") should be > 0
    latest("1.0.0-alpha", "1.0.0-rc.1", "1.0.0") shouldBe "1.0.0"
  }

  it should "order pre-release identifiers per semver precedence" in {
    ordering.compare("1.0.0-rc.2", "1.0.0-rc.1") should be > 0     // numeric identifiers
    ordering.compare("1.0.0-alpha.1", "1.0.0-alpha") should be > 0 // more identifiers
    ordering.compare("1.0.0-beta", "1.0.0-alpha") should be > 0    // alphanumeric identifiers
    ordering.compare("1.0.0-1", "1.0.0-alpha") should be < 0       // numeric outranked by alphanumeric
  }

  it should "ignore build metadata for precedence" in {
    ordering.compare("1.0.0+build.5", "1.0.0+build.1") shouldBe 0
    ordering.compare("1.0.0+build", "1.0.0") shouldBe 0
  }

  it should "order any non-semver string below every valid semantic version" in {
    ordering.compare("latest", "0.0.1") should be < 0
    latest("1.0.0", "2.0.0", "custom-tag") shouldBe "2.0.0"
  }

  it should "fall back to lexicographic order among non-semver strings" in {
    ordering.compare("alpha", "beta") should be < 0
    latest("rc-1", "rc-2") shouldBe "rc-2"
  }
}
