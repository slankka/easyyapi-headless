package com.itangcent.easyapi.core.export.recognizer

import com.itangcent.easyapi.core.settings.ui.SettingsPanelProvider

/**
 * Recognizes whether a [PsiClass] is an API class for a specific framework.
 *
 * Each framework (Spring MVC, JAX-RS, Feign, etc.) provides its own implementation.
 * Use [CompositeApiClassRecognizer] to combine them.
 *
 * ## Extension Point
 *
 * Implementations are discovered via the `com.itangcent.idea.plugin.easy-api.apiClassRecognizer`
 * extension point (declared in `plugin.xml`). The [CompositeApiClassRecognizer] iterates
 * EP-discovered instances and filters by [isEnabled] — so framework recognizers
 * no longer need to be hard-imported by `core.*` (DAG rule CO3).
 *
 * ## Settings panel
 *
 * Implements [SettingsPanelProvider] so frameworks can optionally contribute a
 * dedicated tab to the EasyApi settings dialog (e.g. the Custom framework's
 * `enableLineMarker` toggle). The default implementation returns `null` (no
 * panel) — most frameworks don't need a settings tab of their own.
 */
interface ApiClassRecognizer : HeadlessApiClassRecognizer, SettingsPanelProvider
