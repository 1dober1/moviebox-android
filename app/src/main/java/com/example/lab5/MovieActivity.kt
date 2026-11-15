package com.example.lab5

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.snackbar.Snackbar
import com.example.lab5.databinding.ActivityMovieBinding
import java.util.Locale
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MovieActivity : AppCompatActivity() {
    private val model: MovieViewModel by viewModels()
    private var formLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra("movieId", -1)
        if (id == -1) {
            finish()
            return
        }
        enableEdgeToEdge()
        val binding = ActivityMovieBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.favorite.setOnClickListener { model.toggleFavorite(id) }
        binding.save.setOnClickListener {
            model.savePersonal(id, binding.note.text.toString(), binding.watched.isChecked, binding.stars.rating.toInt())
        }
        binding.clearRating.setOnClickListener { binding.stars.rating = 0f }
        binding.retry.setOnClickListener { model.openDetails(id) }
        formLoaded = savedInstanceState?.getBoolean("formLoaded") ?: false
        var displayedMovie: Movie? = null
        if (savedInstanceState == null) model.openDetails(id)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    model.saveMessages.collect { message ->
                        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).setTextMaxLines(3).show()
                    }
                }
                model.state.collect { state ->
                    binding.progress.isVisible = state.ui.loading
                    binding.status.text = state.ui.message
                    binding.status.isVisible = state.ui.message.isNotEmpty()
                    binding.retry.isEnabled = !state.ui.loading
                    val movie = state.movies.find { it.id == id }
                    binding.save.isEnabled = movie != null && !state.ui.saving
                    binding.save.setText(if (state.ui.saving) R.string.saving else R.string.save)
                    binding.favorite.isEnabled = movie != null
                    if (movie != null && movie != displayedMovie) {
                        displayedMovie = movie
                        binding.title.text = movie.title
                        binding.overview.text = movie.overview.ifBlank { "Описание отсутствует." }
                        binding.metadata.text = listOf(
                            "TMDB: " + String.format(Locale.getDefault(), "%.1f/10", movie.rating),
                            "Дата выхода: ${movie.releaseDate.ifBlank { "нет данных" }}",
                            "Жанры: ${movie.genres.ifBlank { "нет данных" }}",
                            "Длительность: ${movie.runtime?.let { "$it мин" } ?: "нет данных"}",
                            "Страны: ${movie.countries.ifBlank { "нет данных" }}"
                        ).joinToString("\n")
                        Glide.with(binding.poster)
                            .load(movie.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" })
                            .diskCacheStrategy(DiskCacheStrategy.DATA)
                            .placeholder(android.R.drawable.ic_menu_gallery)
                            .error(android.R.drawable.ic_menu_gallery).into(binding.poster)
                    }
                    val personal = state.personal[id]
                    if (!formLoaded && movie != null) {
                        binding.note.setText(personal?.note.orEmpty())
                        binding.watched.isChecked = personal?.watched == true
                        binding.stars.rating = personal?.stars?.toFloat() ?: 0f
                        formLoaded = true
                    }
                    binding.favorite.setText(
                        if (personal?.favorite == true) R.string.remove_favorite else R.string.add_favorite
                    )
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("formLoaded", formLoaded)
        super.onSaveInstanceState(outState)
    }
}
