package com.softmusic.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryNavigationTest {
    @Test
    fun historyReturnsToItsOriginAndKeepsTheSelectedList() {
        val history = AppNavigationState(destination = LibraryDestination.Music)
            .openHistory(ListeningHistoryView.MostPlayed)

        assertEquals(LibraryDestination.History, history.destination)
        assertEquals(LibraryDestination.Music, history.selectedNavigationDestination)
        assertEquals("history:most_played", history.screenKey)
        val returned = history.onBack()
        assertEquals(LibraryDestination.Music, returned.destination)
        assertEquals(ListeningHistoryView.MostPlayed, returned.historyView)
    }

    @Test
    fun changingHistoryTabsUsesSeparateSavedScreenKeysWithoutChangingTheReturnRoute() {
        val recent = AppNavigationState(destination = LibraryDestination.Library)
            .openHistory(ListeningHistoryView.Recent)
        val most = recent.selectHistoryView(ListeningHistoryView.MostPlayed)

        assertEquals("history:recent", recent.screenKey)
        assertEquals("history:most_played", most.screenKey)
        assertEquals(LibraryDestination.Library, most.selectedNavigationDestination)
        assertEquals(LibraryDestination.Library, most.returnFromHistory().destination)
    }
}
