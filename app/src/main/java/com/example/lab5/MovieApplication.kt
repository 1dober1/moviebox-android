package com.example.lab5

import android.app.Application
import androidx.room.Room

class MovieApplication : Application() {
    val repository by lazy {
        MovieRepository(Room.databaseBuilder(this, MovieDatabase::class.java, "moviebox.db").build())
    }
}
