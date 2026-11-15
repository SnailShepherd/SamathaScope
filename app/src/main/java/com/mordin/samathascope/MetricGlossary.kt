package com.mordin.samathascope

import androidx.annotation.StringRes

data class MetricGlossaryTerm(
  /** Notation such as "α/β (ABR)"; kept in code because it is not translated. */
  val label: String,
  @StringRes val meaning: Int,
)

data class MetricGlossaryEntry(
  val type: PlotType,
  @StringRes val plainName: Int,
  val abbreviation: String,
  @StringRes val shortMeaning: Int,
  @StringRes val drivers: Int,
  @StringRes val longMeaning: Int,
  @StringRes val displayFormula: Int,
  val canBeFeedbackSource: Boolean,
  @StringRes val interpretiveNote: Int? = null,
  val terms: List<MetricGlossaryTerm> = emptyList(),
)

object MetricGlossary {
  private val entries = listOf(
    MetricGlossaryEntry(
      type = PlotType.MEDITATION_PROXY,
      plainName = R.string.glossary_meditation_proxy_plain_name,
      abbreviation = "MP",
      shortMeaning = R.string.glossary_meditation_proxy_short_meaning,
      drivers = R.string.glossary_meditation_proxy_drivers,
      longMeaning = R.string.glossary_meditation_proxy_long_meaning,
      displayFormula = R.string.glossary_meditation_proxy_display_formula,
      canBeFeedbackSource = true,
      interpretiveNote = R.string.glossary_meditation_proxy_interpretive_note,
    ),
    MetricGlossaryEntry(
      type = PlotType.SETTLEDNESS,
      plainName = R.string.glossary_settledness_plain_name,
      abbreviation = "S",
      shortMeaning = R.string.glossary_settledness_short_meaning,
      drivers = R.string.glossary_settledness_drivers,
      longMeaning = R.string.glossary_settledness_long_meaning,
      displayFormula = R.string.glossary_settledness_display_formula,
      canBeFeedbackSource = true,
      interpretiveNote = R.string.glossary_settledness_interpretive_note,
      terms = listOf(
        MetricGlossaryTerm("α/β (ABR)", R.string.glossary_settledness_term_1),
        MetricGlossaryTerm("θ/α (TAR)", R.string.glossary_settledness_term_2),
      ),
    ),
    MetricGlossaryEntry(
      type = PlotType.CONTROL,
      plainName = R.string.glossary_control_plain_name,
      abbreviation = "C",
      shortMeaning = R.string.glossary_control_short_meaning,
      drivers = R.string.glossary_control_drivers,
      longMeaning = R.string.glossary_control_long_meaning,
      displayFormula = R.string.glossary_control_display_formula,
      canBeFeedbackSource = false,
      interpretiveNote = R.string.glossary_control_interpretive_note,
      terms = listOf(
        MetricGlossaryTerm("θ/β (TBR)", R.string.glossary_control_term_1),
      ),
    ),
    MetricGlossaryEntry(
      type = PlotType.ALERTNESS,
      plainName = R.string.glossary_alertness_plain_name,
      abbreviation = "A",
      shortMeaning = R.string.glossary_alertness_short_meaning,
      drivers = R.string.glossary_alertness_drivers,
      longMeaning = R.string.glossary_alertness_long_meaning,
      displayFormula = R.string.glossary_alertness_display_formula,
      canBeFeedbackSource = true,
      interpretiveNote = R.string.glossary_alertness_interpretive_note,
    ),
    MetricGlossaryEntry(
      type = PlotType.DROWSY_SCORE,
      plainName = R.string.glossary_drowsy_score_plain_name,
      abbreviation = "D",
      shortMeaning = R.string.glossary_drowsy_score_short_meaning,
      drivers = R.string.glossary_drowsy_score_drivers,
      longMeaning = R.string.glossary_drowsy_score_long_meaning,
      displayFormula = R.string.glossary_drowsy_score_display_formula,
      canBeFeedbackSource = false,
      interpretiveNote = R.string.glossary_drowsy_score_interpretive_note,
      terms = listOf(
        MetricGlossaryTerm("θ/α (TAR)", R.string.glossary_drowsy_score_term_1),
        MetricGlossaryTerm("θ/β (TBR)", R.string.glossary_drowsy_score_term_2),
        MetricGlossaryTerm("H (Entropy)", R.string.glossary_drowsy_score_term_3),
        MetricGlossaryTerm("α/β (ABR)", R.string.glossary_drowsy_score_term_4),
      ),
    ),
    MetricGlossaryEntry(
      type = PlotType.ARTEFACT_SCORE,
      plainName = R.string.glossary_artefact_score_plain_name,
      abbreviation = "AS",
      shortMeaning = R.string.glossary_artefact_score_short_meaning,
      drivers = R.string.glossary_artefact_score_drivers,
      longMeaning = R.string.glossary_artefact_score_long_meaning,
      displayFormula = R.string.glossary_artefact_score_display_formula,
      canBeFeedbackSource = false,
      interpretiveNote = R.string.glossary_artefact_score_interpretive_note,
    ),
    MetricGlossaryEntry(
      type = PlotType.QUALITY_CONFIDENCE,
      plainName = R.string.glossary_quality_confidence_plain_name,
      abbreviation = "QC",
      shortMeaning = R.string.glossary_quality_confidence_short_meaning,
      drivers = R.string.glossary_quality_confidence_drivers,
      longMeaning = R.string.glossary_quality_confidence_long_meaning,
      displayFormula = R.string.glossary_quality_confidence_display_formula,
      canBeFeedbackSource = false,
      interpretiveNote = R.string.glossary_quality_confidence_interpretive_note,
    ),
    MetricGlossaryEntry(
      type = PlotType.EFFORTFUL_FOCUS_SCORE,
      plainName = R.string.glossary_effortful_focus_score_plain_name,
      abbreviation = "F",
      shortMeaning = R.string.glossary_effortful_focus_score_short_meaning,
      drivers = R.string.glossary_effortful_focus_score_drivers,
      longMeaning = R.string.glossary_effortful_focus_score_long_meaning,
      displayFormula = R.string.glossary_effortful_focus_score_display_formula,
      canBeFeedbackSource = false,
      interpretiveNote = R.string.glossary_effortful_focus_score_interpretive_note,
    ),
    MetricGlossaryEntry(
      type = PlotType.MIND_WANDERING_SCORE,
      plainName = R.string.glossary_mind_wandering_score_plain_name,
      abbreviation = "W",
      shortMeaning = R.string.glossary_mind_wandering_score_short_meaning,
      drivers = R.string.glossary_mind_wandering_score_drivers,
      longMeaning = R.string.glossary_mind_wandering_score_long_meaning,
      displayFormula = R.string.glossary_mind_wandering_score_display_formula,
      canBeFeedbackSource = false,
      interpretiveNote = R.string.glossary_mind_wandering_score_interpretive_note,
      terms = listOf(
        MetricGlossaryTerm("θ/β (TBR)", R.string.glossary_mind_wandering_score_term_1),
        MetricGlossaryTerm("α/β (ABR)", R.string.glossary_mind_wandering_score_term_2),
        MetricGlossaryTerm("H (Entropy)", R.string.glossary_mind_wandering_score_term_3),
      ),
    ),
    MetricGlossaryEntry(
      type = PlotType.ESENSE_MEDITATION,
      plainName = R.string.glossary_esense_meditation_plain_name,
      abbreviation = "EMed",
      shortMeaning = R.string.glossary_esense_meditation_short_meaning,
      drivers = R.string.glossary_esense_meditation_drivers,
      longMeaning = R.string.glossary_esense_meditation_long_meaning,
      displayFormula = R.string.glossary_esense_meditation_display_formula,
      canBeFeedbackSource = false,
      interpretiveNote = R.string.glossary_esense_meditation_interpretive_note,
    ),
    MetricGlossaryEntry(
      type = PlotType.ESENSE_ATTENTION,
      plainName = R.string.glossary_esense_attention_plain_name,
      abbreviation = "EAtt",
      shortMeaning = R.string.glossary_esense_attention_short_meaning,
      drivers = R.string.glossary_esense_attention_drivers,
      longMeaning = R.string.glossary_esense_attention_long_meaning,
      displayFormula = R.string.glossary_esense_attention_display_formula,
      canBeFeedbackSource = false,
      interpretiveNote = R.string.glossary_esense_attention_interpretive_note,
    ),
    MetricGlossaryEntry(
      type = PlotType.RAW,
      plainName = R.string.glossary_raw_plain_name,
      abbreviation = "Raw",
      shortMeaning = R.string.glossary_raw_short_meaning,
      drivers = R.string.glossary_raw_drivers,
      longMeaning = R.string.glossary_raw_long_meaning,
      displayFormula = R.string.glossary_raw_display_formula,
      canBeFeedbackSource = false,
      interpretiveNote = R.string.glossary_raw_interpretive_note,
    ),
  ).associateBy { it.type }

  fun entryFor(type: PlotType): MetricGlossaryEntry = entries.getValue(type)

  fun dashboardMetrics(): List<PlotType> = listOf(
    PlotType.MEDITATION_PROXY,
    PlotType.SETTLEDNESS,
    PlotType.CONTROL,
    PlotType.ALERTNESS,
    PlotType.ARTEFACT_SCORE,
    PlotType.EFFORTFUL_FOCUS_SCORE,
    PlotType.MIND_WANDERING_SCORE,
    PlotType.ESENSE_MEDITATION,
    PlotType.ESENSE_ATTENTION,
  )

  fun feedbackSourceMetrics(): List<PlotType> = listOf(
    PlotType.MEDITATION_PROXY,
    PlotType.SETTLEDNESS,
    PlotType.ALERTNESS,
  )

  fun learnEntries(): List<MetricGlossaryEntry> = listOf(
    PlotType.MEDITATION_PROXY,
    PlotType.SETTLEDNESS,
    PlotType.CONTROL,
    PlotType.ALERTNESS,
    PlotType.DROWSY_SCORE,
    PlotType.ARTEFACT_SCORE,
    PlotType.QUALITY_CONFIDENCE,
    PlotType.EFFORTFUL_FOCUS_SCORE,
    PlotType.MIND_WANDERING_SCORE,
    PlotType.ESENSE_MEDITATION,
    PlotType.ESENSE_ATTENTION,
    PlotType.RAW,
  ).map(::entryFor)
}
