package com.itangcent.easyapi.core.export.recognizer

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.itangcent.easyapi.core.rule.RuleKey

/**
 * GUI-free framework recognizer contract used by the headless extraction
 * pipeline. IDEA-only settings panels belong to the plugin-side adapter
 * [ApiClassRecognizer].
 */
interface HeadlessApiClassRecognizer {
    /** Stable framework identifier shared by recognizers and exporters. */
    val frameworkName: String

    /** Annotation FQNs that can identify API classes during indexed scanning. */
    val targetAnnotations: Set<String>

    /** Returns whether [psiClass] is an API class for this framework. */
    suspend fun isApiClass(psiClass: PsiClass): Boolean

    /** Project-specific enablement gate, defaulting to enabled. */
    fun isEnabled(project: Project): Boolean = true

    /** Default state before project preferences are applied. */
    val enabledByDefault: Boolean get() = true

    /** Framework-specific rule keys contributed to the rule catalog. */
    fun ruleKeys(): List<RuleKey<*>> = emptyList()

    /** Cheap recognition path for IDE integrations that need a fast check. */
    fun matchesClass(psiClass: PsiClass): Boolean = false
}
