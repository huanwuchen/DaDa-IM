package com.dada.app.ui.image

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.dada.app.databinding.ItemImagePreviewBinding
import com.dada.core.imageloader.ImageLoader
import com.dada.core.imageloader.loadImage

class ImagePreviewAdapter(
    private val imageUrls: List<String>,
    private val imageLoader: ImageLoader,
    private val onImageClick: () -> Unit
) : RecyclerView.Adapter<ImagePreviewAdapter.ImageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val binding = ItemImagePreviewBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ImageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        holder.bind(imageUrls[position])
    }

    override fun getItemCount(): Int = imageUrls.size

    inner class ImageViewHolder(
        private val binding: ItemImagePreviewBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(imageUrl: String) {
            binding.photoView.loadImage(imageUrl, imageLoader)
            binding.photoView.setOnClickListener { onImageClick() }
        }
    }
}

