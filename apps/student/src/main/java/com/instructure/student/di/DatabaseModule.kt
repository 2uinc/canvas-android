package com.instructure.student.di

import android.content.Context
import androidx.room.Room
import com.instructure.pandautils.room.appdatabase.AppDatabase
import com.instructure.pandautils.room.appdatabase.appDatabaseMigrations
import com.instructure.pandautils.room.calendar.CalendarFilterDatabase
import com.instructure.student.db.Db
import com.instructure.student.db.StudentDb
import com.instructure.student.db.getInstance
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "db-canvas-student")
            .addMigrations(*appDatabaseMigrations)
            .build()
    }

    @Provides
    @Singleton
    fun provideSqlDelightDatabase(@ApplicationContext context: Context): StudentDb {
        return Db.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideCalendarDatabase(@ApplicationContext context: Context): CalendarFilterDatabase {
        return Room.databaseBuilder(
            context,
            CalendarFilterDatabase::class.java,
            "canvas_student_flutter.db"
        ) // We need to have the same db name as in the Flutter calendar
            .build()
    }
}