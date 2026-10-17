package com.routewise.sa.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.routewise.sa.data.model.IncidentEntity
import com.routewise.sa.data.model.RouteStepEntity
import com.routewise.sa.data.model.StopSuggestionEntity
import com.routewise.sa.data.model.UserEntity

@Database(
    entities = [
        UserEntity::class,
        IncidentEntity::class,
        RouteStepEntity::class,
        StopSuggestionEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class RouteWiseDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun incidentDao(): IncidentDao

    companion object {
        @Volatile
        private var INSTANCE: RouteWiseDatabase? = null

        fun getInstance(context: Context): RouteWiseDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RouteWiseDatabase::class.java,
                    "routewise_sa_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
