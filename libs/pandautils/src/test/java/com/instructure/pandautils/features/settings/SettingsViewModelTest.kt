/*
 * Copyright (C) 2024 - present Instructure, Inc.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 */
package com.instructure.pandautils.features.settings

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.MutableLiveData
import com.instructure.pandautils.R
import com.instructure.pandautils.features.offline.sync.settings.SyncFrequency
import com.instructure.pandautils.room.offline.entities.SyncSettingsEntity
import com.instructure.pandautils.room.offline.facade.SyncSettingsFacade
import com.instructure.pandautils.utils.AppTheme
import com.instructure.pandautils.utils.ThemePrefs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = UnconfinedTestDispatcher()

    private val lifecycleOwner: LifecycleOwner = mockk(relaxed = true)
    private val lifecycleRegistry = LifecycleRegistry(lifecycleOwner)

    private val settingsBehaviour: SettingsBehaviour = mockk(relaxed = true)
    private val syncSettingsFacade: SyncSettingsFacade = mockk(relaxed = true)
    private val themePrefs: ThemePrefs = mockk(relaxed = true)

    @Before
    fun setup() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        unmockkAll()
        Dispatchers.resetMain()
    }

    @Test
    fun `Behaviour maps correctly`() {
        val items = mapOf(
            R.string.preferences to listOf(
                SettingsItem.APP_THEME,
                SettingsItem.PROFILE_SETTINGS,
                SettingsItem.PUSH_NOTIFICATIONS,
                SettingsItem.EMAIL_NOTIFICATIONS
            ),
            R.string.legal to listOf(SettingsItem.ABOUT, SettingsItem.LEGAL)
        )
        every { settingsBehaviour.settingsItems } returns items

        every { themePrefs.appTheme } returns 0

        val viewModel = createViewModel()

        val uiState = viewModel.uiState.value

        assertEquals(R.string.appThemeLight, uiState.appTheme)
        assertEquals(items, uiState.items)
    }

    @Test
    fun `Change app theme`() {
        val items = mapOf(
            R.string.preferences to listOf(
                SettingsItem.APP_THEME,
                SettingsItem.PROFILE_SETTINGS,
                SettingsItem.PUSH_NOTIFICATIONS,
                SettingsItem.EMAIL_NOTIFICATIONS
            ),
            R.string.legal to listOf(SettingsItem.ABOUT, SettingsItem.LEGAL)
        )
        every { settingsBehaviour.settingsItems } returns items

        every { themePrefs.appTheme } returns 0

        val viewModel = createViewModel()

        viewModel.onThemeSelected(AppTheme.DARK)

        val uiState = viewModel.uiState.value

        assertEquals(R.string.appThemeDark, uiState.appTheme)
    }

    @Test
    fun `Offline sync settings`() = runTest {
        val syncSettingsLiveData =
            MutableLiveData(SyncSettingsEntity(1L, false, SyncFrequency.DAILY, false))
        coEvery { syncSettingsFacade.getSyncSettingsListenable() } returns syncSettingsLiveData

        val items = mapOf(
            R.string.offlineSyncSettingsTitle to listOf(
                SettingsItem.OFFLINE_SYNCHRONIZATION
            )
        )

        every { settingsBehaviour.settingsItems } returns items

        every { themePrefs.appTheme } returns 0

        val viewModel = createViewModel()

        val uiState = viewModel.uiState.value

        assertEquals(R.string.syncSettings_manualDescription, uiState.offlineState)

        syncSettingsLiveData.value = SyncSettingsEntity(1L, true, SyncFrequency.DAILY, false)
        assertEquals(SyncFrequency.DAILY.readable, viewModel.uiState.value.offlineState)

        syncSettingsLiveData.value = SyncSettingsEntity(1L, true, SyncFrequency.WEEKLY, false)
        assertEquals(SyncFrequency.WEEKLY.readable, viewModel.uiState.value.offlineState)
    }

    @Test
    fun `item click`() = runTest {
        val items = mapOf(
            R.string.preferences to listOf(
                SettingsItem.APP_THEME,
                SettingsItem.PROFILE_SETTINGS,
                SettingsItem.PUSH_NOTIFICATIONS,
                SettingsItem.EMAIL_NOTIFICATIONS
            ),
            R.string.legal to listOf(SettingsItem.ABOUT, SettingsItem.LEGAL)
        )
        every { settingsBehaviour.settingsItems } returns items

        every { themePrefs.appTheme } returns 0

        val viewModel = createViewModel()

        viewModel.uiState.value.items.flatMap { it.value }.forEach { item ->
            viewModel.uiState.value.onClick(item)
            val events = mutableListOf<SettingsViewModelAction>()
            backgroundScope.launch(testDispatcher) {
                viewModel.events.toList(events)
                assertEquals(SettingsViewModelAction.Navigate(item), events.last())
            }
        }
    }


    private fun createViewModel(): SettingsViewModel {
        return SettingsViewModel(settingsBehaviour, themePrefs, syncSettingsFacade)
    }
}