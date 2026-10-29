package com.softmusic.app.icons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherIconSwitcherTest {
    @Test
    fun aVisibleActivityKeepsItsLaunchingAliasUntilTheAppGoesToBackground() {
        val store = FakeStore(atomic = true, SeasonalIcon.Halloween)
        val switcher = LauncherIconSwitcher(store)
        assertFalse(switcher.apply(SeasonalIcon.DayOfTheDead, appIsVisible = true))
        assertEquals(setOf(SeasonalIcon.Halloween), store.enabled)
        assertTrue(store.changes.isEmpty())
        assertTrue(switcher.apply(SeasonalIcon.DayOfTheDead, appIsVisible = false))
        assertEquals(setOf(SeasonalIcon.DayOfTheDead), store.enabled)
    }

    @Test
    fun theCorrectSingleIconDoesNotTriggerAnyPackageChanges() {
        val store = FakeStore(atomic = false, SeasonalIcon.Halloween)
        assertFalse(LauncherIconSwitcher(store).apply(SeasonalIcon.Halloween))
        assertTrue(store.changes.isEmpty())
    }

    @Test
    fun modernAndroidChangesAllAliasesInOneAtomicOperation() {
        val store = FakeStore(atomic = true, SeasonalIcon.Original)
        val switcher = LauncherIconSwitcher(store)
        assertTrue(switcher.apply(SeasonalIcon.Anniversary))
        assertEquals(setOf(SeasonalIcon.Anniversary), store.enabled)
        assertEquals(listOf("atomic:Anniversary"), store.changes)
        assertFalse(switcher.apply(SeasonalIcon.Anniversary))
        assertEquals(1, store.changes.size)
    }

    @Test
    fun olderAndroidAlwaysHasALaunchableIconDuringAChange() {
        val store = FakeStore(atomic = false, SeasonalIcon.Halloween)
        LauncherIconSwitcher(store).apply(SeasonalIcon.DayOfTheDead)
        assertEquals(listOf("enable:DayOfTheDead", "disable:Halloween"), store.changes)
        assertTrue(store.states.all { it.isNotEmpty() })
        assertEquals(setOf(SeasonalIcon.DayOfTheDead), store.enabled)
    }

    @Test
    fun aFailedEnableLeavesTheExistingIconAvailableForRecovery() {
        val store = FakeStore(atomic = false, SeasonalIcon.Original)
        store.failEnable = true
        assertThrows(IllegalStateException::class.java) {
            LauncherIconSwitcher(store).apply(SeasonalIcon.Christmas)
        }
        assertEquals(setOf(SeasonalIcon.Original), store.enabled)
        assertTrue(store.changes.isEmpty())
    }

    @Test
    fun duplicateAliasesAreRepairedWithoutDisablingTheDesiredIcon() {
        val store = FakeStore(atomic = false, SeasonalIcon.Original, SeasonalIcon.NewYear)
        LauncherIconSwitcher(store).apply(SeasonalIcon.NewYear)
        assertEquals(listOf("disable:Original"), store.changes)
        assertEquals(setOf(SeasonalIcon.NewYear), store.enabled)
    }

    @Test
    fun aMissingEnabledAliasIsRecoveredByEnablingTheSelectedOne() {
        val store = FakeStore(atomic = false)
        LauncherIconSwitcher(store).apply(SeasonalIcon.Original)
        assertEquals(listOf("enable:Original"), store.changes)
        assertEquals(setOf(SeasonalIcon.Original), store.enabled)
    }

    private class FakeStore(atomic: Boolean, vararg initial: SeasonalIcon) : LauncherIconStore {
        override val supportsAtomicChanges = atomic
        var enabled = initial.toSet()
        var failEnable = false
        val changes = mutableListOf<String>()
        val states = mutableListOf<Set<SeasonalIcon>>()
        override fun enabledIcons(): Set<SeasonalIcon> = enabled
        override fun replaceEnabledIcon(icon: SeasonalIcon) {
            changes += "atomic:${icon.name}"
            enabled = setOf(icon)
            states += enabled
        }
        override fun setEnabled(icon: SeasonalIcon, enabled: Boolean) {
            if (enabled && failEnable) throw IllegalStateException("Unable to enable replacement")
            changes += "${if (enabled) "enable" else "disable"}:${icon.name}"
            this.enabled = if (enabled) this.enabled + icon else this.enabled - icon
            states += this.enabled
        }
    }
}
