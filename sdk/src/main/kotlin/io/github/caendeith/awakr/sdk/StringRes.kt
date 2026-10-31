package io.github.caendeith.awakr.sdk

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * A string owned by a plugin module.
 *
 * [resId] points into the plugin module's own resources (all modules are linked into the host
 * application at build time), [fallback] is used if the resource is missing.
 */
@AwakrApi
data class StringRes(val resId: Int, val fallback: String = "")

/** Resolves [StringRes] inside a composable. */
@Composable
@AwakrApi
fun StringRes.resolve(): String =
    if (resId != 0) stringResource(resId) else fallback
