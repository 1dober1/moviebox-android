package com.example.lab5

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.lab5.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val model: MovieViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            binding.navigation.isVisible = !insets.isVisible(WindowInsetsCompat.Type.ime())
            insets
        }
        val adapter = MovieAdapter(
            open = { id -> startActivity(Intent(this, MovieActivity::class.java).putExtra("movieId", id)) },
            favorite = model::toggleFavorite
        )
        binding.movies.layoutManager = LinearLayoutManager(this)
        binding.movies.adapter = adapter
        binding.navigation.setOnItemSelectedListener {
            model.selectTab(when (it.itemId) { R.id.search -> 1; R.id.favorites -> 2; R.id.watched -> 3; else -> 0 })
            true
        }
        binding.toolbar.setOnMenuItemClickListener {
            binding.navigation.selectedItemId = R.id.search
            binding.searchInput.requestFocus()
            true
        }
        binding.searchInput.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                model.search(query)
                binding.searchInput.clearFocus()
                return true
            }
            override fun onQueryTextChange(newText: String) = false
        })
        val filters = model.state.value.ui.watchedFilters
        binding.ratingFilter.setSelection(filters.stars + 1)
        binding.favoritesFilter.isChecked = filters.favoritesOnly
        binding.noteFilter.isChecked = filters.withNote
        fun applyWatchedFilters() {
            model.setWatchedFilters(
                binding.ratingFilter.selectedItemPosition - 1,
                binding.favoritesFilter.isChecked,
                binding.noteFilter.isChecked
            )
        }
        binding.ratingFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                applyWatchedFilters()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        binding.favoritesFilter.setOnCheckedChangeListener { _, _ -> applyWatchedFilters() }
        binding.noteFilter.setOnCheckedChangeListener { _, _ -> applyWatchedFilters() }
        binding.retry.setOnClickListener { model.load() }
        binding.more.setOnClickListener { model.load(more = true) }
        model.start()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.state.collect { state ->
                    val ui = state.ui
                    val movies = state.visibleMovies()
                    val filters = ui.watchedFilters
                    binding.searchInput.isVisible = ui.tab == 1
                    binding.watchedFilters.isVisible = ui.tab == 3
                    binding.ratingFilter.setSelection(filters.stars + 1)
                    binding.favoritesFilter.isChecked = filters.favoritesOnly
                    binding.noteFilter.isChecked = filters.withNote
                    binding.progress.isVisible = ui.loading
                    binding.retry.isVisible = ui.tab < 2 && (ui.tab == 0 || ui.query.isNotEmpty())
                    binding.retry.isEnabled = !ui.loading
                    binding.more.isVisible = ui.tab < 2 && ui.page > 0 && ui.page < ui.totalPages
                    binding.more.isEnabled = !ui.loading
                    binding.status.text = ui.message.ifEmpty {
                        when {
                            ui.loading -> "Загрузка…"
                            ui.tab == 1 && ui.query.isEmpty() -> "Введите название и нажмите поиск на клавиатуре."
                            movies.isEmpty() && ui.tab == 3 -> getString(
                                if (filters.stars != -1 || filters.favoritesOnly || filters.withNote)
                                    R.string.watched_no_matches else R.string.watched_empty
                            )
                            movies.isEmpty() && ui.tab == 2 -> "В избранном пока нет фильмов."
                            movies.isEmpty() -> "Ничего не найдено."
                            else -> "Фильмов: ${movies.size}"
                        }
                    }
                    binding.navigation.menu.getItem(ui.tab).isChecked = true
                    adapter.submitList(movies.map { MovieRow(it, state.personal[it.id], ui.tab >= 2) })
                }
            }
        }
    }
}
