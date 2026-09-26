package com.exteragram.messenger.icons.ui.picker

import com.exteragram.messenger.ExteraConfig
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.LaunchActivity
import java.util.WeakHashMap

object IconObserver {

    private val iconSources = WeakHashMap<BaseFragment, MutableSet<Int>>()

    fun log(resId: Int) {
        if (ExteraConfig.editingIconPackId == null) {
            return
        }
        val fragment = LaunchActivity.getSafeLastFragment() ?: return
        (fragment.parentActivity as? LaunchActivity)?.let {
            IconPickerController.setActive(it, true)
        }
        synchronized(iconSources) {
            iconSources.getOrPut(fragment) { HashSet() }.add(resId)
        }
    }

    fun removeSource(fragment: BaseFragment) {
        synchronized(iconSources) {
            iconSources.remove(fragment)
        }
    }

    val usedIcons: Set<Int>
        get() {
            val visibleFragments = LaunchActivity.getVisibleFragments()
            synchronized(iconSources) {
                return visibleFragments.flatMap { iconSources[it] ?: emptySet() }.toSet()
            }
        }

    fun clear() {
        synchronized(iconSources) {
            iconSources.clear()
        }
    }
}
