package com.example.lab5

import androidx.room.withTransaction
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

data class NamedValue(val name: String)

data class MovieResponse(
    val id: Int,
    val title: String,
    val overview: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("release_date") val releaseDate: String?,
    @SerializedName("vote_average") val rating: Double,
    val genres: List<NamedValue>?,
    val runtime: Int?,
    @SerializedName("production_countries") val countries: List<NamedValue>?
) {
    fun toMovie(details: Boolean = false) = Movie(
        id, title, overview.orEmpty(), posterPath, releaseDate.orEmpty(), rating,
        genres.orEmpty().joinToString { it.name }, runtime,
        countries.orEmpty().joinToString { it.name }, details
    )
}

data class MoviePage(val results: List<MovieResponse>, @SerializedName("total_pages") val totalPages: Int)

interface MovieApi {
    @GET("movie/popular")
    suspend fun popular(@Query("page") page: Int): MoviePage

    @GET("search/movie")
    suspend fun search(@Query("query") query: String, @Query("page") page: Int): MoviePage

    @GET("movie/{id}")
    suspend fun details(@Path("id") id: Int): MovieResponse
}

data class LoadedPage(val ids: List<Int>, val totalPages: Int, val cached: Boolean)

class MovieRepository(private val db: MovieDatabase) {
    val dao = db.movies()
    private val api = Retrofit.Builder()
        .baseUrl("https://api.themoviedb.org/3/")
        .client(OkHttpClient.Builder().addInterceptor { chain ->
            val url = chain.request().url.newBuilder()
                .addQueryParameter("api_key", BuildConfig.TMDB_API_KEY)
                .addQueryParameter("language", "ru-RU").build()
            chain.proceed(chain.request().newBuilder().url(url).build())
        }.build())
        .addConverterFactory(GsonConverterFactory.create())
        .build().create(MovieApi::class.java)

    suspend fun loadPage(query: String, page: Int): LoadedPage {
        check(BuildConfig.TMDB_API_KEY.isNotBlank()) { "Добавьте TMDB_API_KEY в local.properties и пересоберите приложение." }
        val response = if (query.isEmpty()) api.popular(page) else api.search(query, page)
        db.withTransaction {
            val movies = response.results.map { result ->
                val previous = dao.movie(result.id)
                result.toMovie().let { movie ->
                    if (previous?.detailsLoaded == true) movie.copy(
                        genres = previous.genres, runtime = previous.runtime,
                        countries = previous.countries, detailsLoaded = true
                    ) else movie
                }
            }
            dao.saveMovies(movies)
            dao.clearPage(query, page)
            dao.saveEntries(movies.mapIndexed { index, movie -> FeedEntry(query, page, index, movie.id) })
            dao.savePage(CachedPage(query, page, response.totalPages.coerceAtMost(500)))
        }
        return LoadedPage(response.results.map { it.id }, response.totalPages.coerceAtMost(500), false)
    }

    suspend fun cachedPage(query: String, page: Int): LoadedPage? {
        val cached = dao.cachedPage(query, page) ?: return null
        return LoadedPage(dao.pageIds(query, page), cached.totalPages, true)
    }

    suspend fun details(id: Int) {
        check(BuildConfig.TMDB_API_KEY.isNotBlank()) { "Для загрузки деталей добавьте TMDB_API_KEY и пересоберите приложение." }
        val movie = api.details(id).toMovie(details = true)
        dao.saveMovies(listOf(movie))
    }

    suspend fun toggleFavorite(id: Int) = db.withTransaction {
        val current = dao.personal(id) ?: Personal(id)
        dao.savePersonal(current.copy(favorite = !current.favorite))
    }

    suspend fun savePersonal(id: Int, note: String, watched: Boolean, stars: Int) = db.withTransaction {
        val current = dao.personal(id) ?: Personal(id)
        dao.savePersonal(current.copy(note = note, watched = watched, stars = stars))
    }
}
