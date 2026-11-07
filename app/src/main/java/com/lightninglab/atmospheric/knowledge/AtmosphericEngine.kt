package com.lightninglab.atmospheric.knowledge

import android.content.Context

/**
 * Phase-1 knowledge engine.
 *
 * Deliberately separates a model's prediction from the scientific reference report.
 * A future classifier should pass its predicted code and calibrated score here.
 */
class AtmosphericEngine(context: Context) {
    private val knowledge = AtmosphericKnowledgeRepository(context)
    private val morphology = CloudMorphologyAnalyzer(knowledge)
    private val evolution = CloudEvolutionAnalyzer()

    fun searchKnowledge(query: String): List<PhenomenonRecord> = knowledge.search(query)

    fun buildReferenceReport(phenomenonCode: String): AtmosphericKnowledgeReport? =
        knowledge.reportFor(phenomenonCode)

    fun cloudCatalog(): List<PhenomenonRecord> = knowledge.cloudGenera()

    /** Assess species and supplementary-feature candidates from explicitly supplied visual cues. */
    fun assessCloudMorphology(evidence: CloudVisualEvidence): MorphologyAssessment =
        morphology.assess(evidence)

    /** Analyze visible cloud changes across observations sampled from a video. */
    fun analyzeCloudEvolution(frames: List<CloudFrameObservation>): CloudEvolutionReport =
        evolution.analyze(frames)
}
