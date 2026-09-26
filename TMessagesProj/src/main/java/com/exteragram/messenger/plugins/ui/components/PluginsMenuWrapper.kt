package com.exteragram.messenger.plugins.ui.components

import android.widget.LinearLayout
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.PopupSwipeBackLayout

/**
 * Lite build: plugins are not available, the wrapper only provides an empty swipe-back layout.
 */
abstract class PluginsMenuWrapper(
    swipeBackLayout: PopupSwipeBackLayout,
    items: List<Any>?,
    location: String?,
    context: Map<String, Any?>?,
    resourcesProvider: Theme.ResourcesProvider?
) {
    val swipeBack = LinearLayout(swipeBackLayout.context)

    open fun rebuildMenu(items: List<Any>?) {
    }
}
