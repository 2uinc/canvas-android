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

package com.instructure.parentapp.di.feature

import android.content.Context
import com.instructure.canvasapi2.utils.ApiPrefs
import com.instructure.pandautils.features.about.AboutRepository
import com.instructure.parentapp.features.about.ParentAboutRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext

@Module
@InstallIn(ViewModelComponent::class)
class AboutModule {

    @Provides
    fun provideAboutRepository(
        @ApplicationContext context: Context,
        apiPrefs: ApiPrefs
    ): AboutRepository {
        return ParentAboutRepository(context, apiPrefs)
    }
}
