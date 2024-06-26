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
package com.instructure.canvas.espresso.common.pages.compose

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.instructure.espresso.page.BasePage
import com.instructure.espresso.page.getStringFromResource
import com.instructure.pandautils.R
import org.junit.Assert.assertTrue

class CalendarScreenPage(private val composeTestRule: ComposeTestRule) : BasePage() {

    fun assertCalendarPageTitle() {
        composeTestRule.onNodeWithText("Calendar").assertIsDisplayed()
    }

    fun clickOnAddButton() {
        composeTestRule.onNodeWithContentDescription(getStringFromResource(R.string.calendarAddButtonContentDescription))
            .performClick()
        composeTestRule.waitForIdle()
    }

    fun clickAddEvent() {
        composeTestRule.onNodeWithText("Add Event").performClick()
        composeTestRule.waitForIdle()
    }

    fun clickAddTodo() {
        composeTestRule.onNodeWithText("Add To Do").performClick()
        composeTestRule.waitForIdle()
    }

    fun clickOnItem(itemTitle: String) {
        composeTestRule.onNodeWithText(itemTitle).performClick()
        composeTestRule.waitForIdle()
    }

    fun assertItemDetails(eventTitle: String, contextName: String, eventDate: String? = null, eventStatus: String? = null) {
        composeTestRule.onNodeWithText(eventTitle).assertIsDisplayed()
        composeTestRule.onNodeWithText(contextName).assertIsDisplayed()
        if(eventDate != null) composeTestRule.onNodeWithText(eventDate).assertIsDisplayed()
        if(eventStatus != null) composeTestRule.onNodeWithText(eventStatus).assertIsDisplayed()
    }

    fun assertEmptyView() {
        assertTrue(composeTestRule.onAllNodesWithTag("calendarEventsEmpty").fetchSemanticsNodes().isNotEmpty())
    }

    fun assertItemNotExist(itemTitle: String) {
        composeTestRule.onNodeWithText(itemTitle).assertDoesNotExist()
    }

}