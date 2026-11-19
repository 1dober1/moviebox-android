package com.example.lab5

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

data class WatchedFilters(val stars: Int = -1, val favoritesOnly: Boolean = false, val withNote: Boolean = false)

data class MovieUi(
    val tab: Int = 0,
    val query: String = "",
    val ids: List<Int> = emptyList(),
    val page: Int = 0,
    val totalPages: Int = 0,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val message: String = "",
    val watchedFilters: WatchedFilters = WatchedFilters()
)

data class MovieState(val ui: MovieUi, val movies: List<Movie>, val personal: Map<Int, Personal>) {
    fun visibleMovies(): List<Movie> = if (ui.tab >= 2) {
        val filters = ui.watchedFilters
        movies.filter { movie ->
            val data = personal[movie.id]
            if (ui.tab == 2) data?.favorite == true
            else data != null && data.watched &&
                (filters.stars == -1 || data.stars == filters.stars) &&
                (!filters.favoritesOnly || data.favorite) &&
                (!filters.withNote || data.note.isNotBlank())
        }.sortedBy { it.title }
    } else {
        val byId = movies.associateBy { it.id }
        ui.ids.mapNotNull { byId[it] }
    }
}

class MovieViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as MovieApplication).repository
    private val ui = MutableStateFlow(MovieUi())
    private var loadJob: Job? = null
    private val saveResults = Channel<String>(Channel.BUFFERED)
    val saveMessages = saveResults.receiveAsFlow()
    val state = combine(ui, repository.dao.observeMovies(), repository.dao.observePersonal()) { ui, movies, personal ->
        MovieState(ui, movies, personal.associateBy { it.movieId })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MovieState(ui.value, emptyList(), emptyMap()))

    fun start() {
        if (ui.value.page == 0 && ui.value.message.isEmpty() && ui.value.tab == 0) load()
    }

    fun selectTab(tab: Int) {
        if (ui.value.tab == tab) return
        loadJob?.cancel()
        ui.value = MovieUi(tab = tab, watchedFilters = ui.value.watchedFilters)
        if (tab == 0) load()
    }

    fun search(query: String) {
        loadJob?.cancel()
        ui.value = MovieUi(tab = 1, query = query.trim(), watchedFilters = ui.value.watchedFilters)
        if (query.isNotBlank()) load()
    }

    fun setWatchedFilters(stars: Int, favoritesOnly: Boolean, withNote: Boolean) {
        ui.value = ui.value.copy(watchedFilters = WatchedFilters(stars, favoritesOnly, withNote))
    }

    fun load(more: Boolean = false) {
        val current = ui.value
        if (current.loading || current.tab >= 2 || (current.tab == 1 && current.query.isBlank())) return
        if (more && current.page >= current.totalPages) return
        loadJob = viewModelScope.launch {
            val page = if (more) current.page + 1 else 1
            ui.value = current.copy(loading = true, message = "")
            try {
                val result = repository.loadPage(current.query, page)
                showPage(current, result, page, more)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                try {
                    val cached = repository.cachedPage(current.query, page)
                    if (cached != null) showPage(current, cached, page, more)
                    else ui.value = ui.value.copy(loading = false, message = errorMessage(e))
                } catch (cacheError: Exception) {
                    if (cacheError is CancellationException) throw cacheError
                    ui.value = ui.value.copy(loading = false, message = "Не удалось прочитать локальную базу.")
                }
            }
        }
    }

    private fun showPage(current: MovieUi, result: LoadedPage, page: Int, more: Boolean) {
        ui.value = current.copy(
            ids = (if (more) current.ids + result.ids else result.ids).distinct(),
            page = page, totalPages = result.totalPages, loading = false,
            message = if (result.cached) "Нет связи с TMDB. Показаны сохранённые данные." else ""
        )
    }

    fun openDetails(id: Int) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            ui.value = ui.value.copy(loading = true, message = "")
            try {
                repository.details(id)
                ui.value = ui.value.copy(loading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ui.value = ui.value.copy(loading = false, message = errorMessage(e) + " Доступны сохранённые сведения.")
            }
        }
    }

    fun toggleFavorite(id: Int) {
        viewModelScope.launch {
            try {
                repository.toggleFavorite(id)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                ui.value = ui.value.copy(message = "Не удалось сохранить избранное.")
            }
        }
    }

    fun savePersonal(id: Int, note: String, watched: Boolean, stars: Int) {
        if (ui.value.saving) return
        viewModelScope.launch {
            val wasWatched = state.value.personal[id]?.watched == true
            ui.value = ui.value.copy(saving = true)
            try {
                repository.savePersonal(id, note, watched, stars)
                saveResults.send(when {
                    watched && wasWatched -> "Оценка и заметка обновлены. Фильм в списке «Просмотрено»."
                    watched -> "Фильм добавлен в список «Просмотрено». Изменения сохранены."
                    wasWatched -> "Сохранено. Фильм убран из списка «Просмотрено»."
                    else -> "Заметка сохранена."
                })
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                saveResults.send("Не удалось сохранить изменения. Попробуйте ещё раз.")
            } finally {
                ui.value = ui.value.copy(saving = false)
            }
        }
    }

    private fun errorMessage(e: Exception): String = when (e) {
        is HttpException -> when (e.code()) {
            401 -> "TMDB отклонил API-ключ. Проверьте TMDB_API_KEY."
            429 -> "Слишком много запросов. Попробуйте позже."
            else -> "Ошибка TMDB: ${e.code()}. Попробуйте ещё раз."
        }
        is IOException -> "Не удалось подключиться к TMDB. Проверьте соединение."
        is IllegalStateException -> e.message.orEmpty()
        else -> "Не удалось загрузить данные. Попробуйте ещё раз."
    }
}
