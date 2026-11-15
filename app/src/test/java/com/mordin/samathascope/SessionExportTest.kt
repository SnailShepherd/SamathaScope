package com.mordin.samathascope

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.util.zip.ZipFile
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SessionExportTest {

  @get:Rule
  val temp = TemporaryFolder()

  @Test
  fun latestSessionDir_picksNewestNonEmptySession() {
    val root = temp.newFolder("sessions")
    File(root, "20261101_080000").apply { mkdirs(); File(this, "meta.txt").writeText("a") }
    File(root, "20261102_090000").apply { mkdirs(); File(this, "meta.txt").writeText("b") }
    File(root, "20261103_100000").mkdirs() // Empty: a recording that failed to start.

    assertThat(latestSessionDir(root)!!.name).isEqualTo("20261102_090000")
  }

  @Test
  fun latestSessionDir_isNullWithoutSessions() {
    assertThat(latestSessionDir(File(temp.root, "missing"))).isNull()
  }

  @Test
  fun zipDirectory_containsEverySessionFileUnderItsFolder() {
    val session = temp.newFolder("20261102_090000")
    File(session, "meta.txt").writeText("sampleRateHz=512\n")
    File(session, "features.csv").writeText("timestamp_ms\n1\n")
    File(session, "raw.raw16le").writeBytes(byteArrayOf(1, 2, 3, 4))
    val zip = File(temp.root, "out.zip")

    zipDirectory(session, zip)

    ZipFile(zip).use { file ->
      val names = file.entries().toList().map { it.name }
      assertThat(names).containsExactly(
        "20261102_090000/features.csv",
        "20261102_090000/meta.txt",
        "20261102_090000/raw.raw16le",
      )
      val raw = file.getInputStream(file.getEntry("20261102_090000/raw.raw16le")).readBytes()
      assertThat(raw).isEqualTo(byteArrayOf(1, 2, 3, 4))
    }
  }

  @Test
  fun pruneOldExports_keepsRecentExportsForPendingShares() {
    val exports = temp.newFolder("exports")
    val now = 10L * 24 * 60 * 60 * 1000
    val old = File(exports, "old.zip").apply { writeText("x"); setLastModified(now - 2L * 24 * 60 * 60 * 1000) }
    val recent = File(exports, "recent.zip").apply { writeText("y"); setLastModified(now - 60_000L) }

    pruneOldExports(exports, nowMs = now)

    assertThat(old.exists()).isFalse()
    assertThat(recent.exists()).isTrue()
  }

  @Test
  fun baselineMetadata_listsMedianAndMadPerFeature() {
    val stat = RobustBaselineStat(median = 0.5f, mad = 0.1f, floor = 0.08f)
    val baseline = Calibration(stat, stat, stat, stat, stat, stat)

    val meta = baselineMetadata(baseline, skipped = false)

    assertThat(meta["calibration"]).isEqualTo("completed")
    assertThat(meta["baseline"]).isEqualTo("calibration")
    assertThat(meta["baseline_tbr_median"]).isEqualTo("0.500000")
    assertThat(meta["baseline_emg_mad"]).isEqualTo("0.100000")
    assertThat(meta.keys.count { it.startsWith("baseline_") }).isEqualTo(12)
  }

  @Test
  fun baselineMetadata_marksMissingBaselineAsProvisional() {
    val meta = baselineMetadata(null, skipped = true)

    assertThat(meta).containsExactly("calibration", "skipped", "baseline", "none_provisional")
  }

  @Test
  fun formatMetadata_writesOneKeyValuePerLine() {
    assertThat(formatMetadata(linkedMapOf("a" to "1", "b" to "two\nlines"))).isEqualTo("a=1\nb=two lines\n")
  }
}
