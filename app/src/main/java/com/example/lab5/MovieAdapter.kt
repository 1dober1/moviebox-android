package com.example.lab5

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.lab5.databinding.ItemMovieBinding
import java.util.Locale

data class MovieRow(val movie: Movie, val personal: Personal?, val showNote: Boolean)

class MovieAdapter(private val open: (Int) -> Unit, private val favorite: (Int) -> Unit) :
    ListAdapter<MovieRow, MovieAdapter.Holder>(object : DiffUtil.ItemCallback<MovieRow>() {
        override fun areItemsTheSame(old: MovieRow, new: MovieRow) = old.movie.id == new.movie.id
        override fun areContentsTheSame(old: MovieRow, new: MovieRow) = old == new
    }) {
    class Holder(val binding: ItemMovieBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemMovieBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val row = getItem(position)
        val movie = row.movie
        with(holder.binding) {
            title.text = movie.title
            metadata.text = "${movie.releaseDate.take(4).ifEmpty { "Год неизвестен" }} · TMDB: " +
                String.format(Locale.getDefault(), "%.1f/10", movie.rating)
            note.isVisible = row.showNote && row.personal != null
            note.text = row.personal?.let { personal ->
                listOfNotNull(
                    if (personal.stars > 0) "Моя оценка: ${personal.stars}/5" else "Моя оценка: не выставлена",
                    "Просмотрено".takeIf { personal.watched },
                    personal.note.takeIf(String::isNotBlank)
                ).joinToString("\n")
            }
            favorite.setText(if (row.personal?.favorite == true) R.string.remove_favorite else R.string.add_favorite)
            favorite.setOnClickListener { this@MovieAdapter.favorite(movie.id) }
            root.setOnClickListener { open(movie.id) }
            Glide.with(poster).load(movie.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" })
                .diskCacheStrategy(DiskCacheStrategy.DATA)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_gallery).into(poster)
        }
    }
}
