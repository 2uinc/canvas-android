/*
 * Copyright (C) 2024 - present Instructure, Inc.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, version 3 of the License.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.instructure.parentapp.ui.compose

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.instructure.parentapp.features.notaparent.NotAParentScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class NotAParentScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun assertContent() {
        composeTestRule.setContent {
            NotAParentScreen(
                returnToLoginClick = {},
                onStudentClick = {},
                onTeacherClick = {}
            )
        }

        composeTestRule.onNodeWithText("Not a parent?").assertIsDisplayed()
        composeTestRule.onNodeWithText("We couldn't find any students associated with your account").assertIsDisplayed()
        composeTestRule.onNodeWithText("Return to login")
            .assertIsDisplayed()
            .assertHasClickAction()
        composeTestRule.onNodeWithText("Are you a student or teacher?")
            .assertIsDisplayed()
            .assertHasClickAction()
        composeTestRule.onNodeWithText("STUDENT")
            .assertIsNotDisplayed()
    }

    @Test
    fun assertAppOptions() {
        composeTestRule.setContent {
            NotAParentScreen(
                returnToLoginClick = {},
                onStudentClick = {},
                onTeacherClick = {}
            )
        }

        composeTestRule.onNodeWithText("Are you a student or teacher?").performClick()
        composeTestRule.onNodeWithText("STUDENT")
            .assertIsDisplayed()
            .assertHasClickAction()
        composeTestRule.onNodeWithText("TEACHER")
            .assertIsDisplayed()
            .assertHasClickAction()
    }
}
