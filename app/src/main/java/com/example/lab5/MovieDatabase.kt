package com.example.lab5

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "movies")
data class Movie(
    @PrimaryKey val id: Int,
    val title: String,
    val overview: String,
    val posterPath: String?,
    val releaseDate: String,
    val rating: Double,
    val genres: String = "",
    val runtime: Int? = null,
    val countries: String = "",
    val detailsLoaded: Boolean = false
)

@Entity(tableName = "personal")
data class Personal(
    @PrimaryKey val movieId: Int,
    val favorite: Boolean = false,
    val note: String = "",
    val watched: Boolean = false,
    val stars: Int = 0
)

@Entity(tableName = "feed", primaryKeys = ["feedKey", "page", "position"])
data class FeedEntry(val feedKey: String, val page: Int, val position: Int, val movieId: Int)

@Entity(tableName = "pages", primaryKeys = ["feedKey", "page"])
data class CachedPage(val feedKey: String, val page: Int, val totalPages: Int)

@Dao
interface MovieDao {
    @Query("SELECT * FROM movies")
    fun observeMovies(): Flow<List<Movie>>

    @Query("SELECT * FROM personal")
    fun observePersonal(): Flow<List<Personal>>

    @Query("SELECT * FROM movies WHERE id = :id")
    suspend fun movie(id: Int): Movie?

    @Query("SELECT * FROM personal WHERE movieId = :id")
    suspend fun personal(id: Int): Personal?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMovies(movies: List<Movie>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePersonal(personal: Personal)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveEntries(entries: List<FeedEntry>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePage(page: CachedPage)

    @Query("DELETE FROM feed WHERE feedKey = :key AND page = :page")
    suspend fun clearPage(key: String, page: Int)

    @Query("SELECT movieId FROM feed WHERE feedKey = :key AND page = :page ORDER BY position")
    suspend fun pageIds(key: String, page: Int): List<Int>

    @Query("SELECT * FROM pages WHERE feedKey = :key AND page = :page")
    suspend fun cachedPage(key: String, page: Int): CachedPage?
}

@Database(entities = [Movie::class, Personal::class, FeedEntry::class, CachedPage::class], version = 1, exportSchema = false)
abstract class MovieDatabase : RoomDatabase() {
    abstract fun movies(): MovieDao
}
