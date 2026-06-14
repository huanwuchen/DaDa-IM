package com.dada.core.common.utils

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding

fun <T, VB : ViewBinding> RecyclerView.setup(
    inflate: (LayoutInflater, ViewGroup, Boolean) -> VB,
    bind: VB.(T) -> Unit,
    itemId: (T) -> Any,
    onItemClick: (T) -> Unit = {},
    orientation: Int = RecyclerView.VERTICAL
): CommonAdapter<T, VB> {
    val adapter = CommonAdapter(inflate, bind, itemId, onItemClick)
    layoutManager = LinearLayoutManager(context, orientation, false)
    this.adapter = adapter
    return adapter
}

fun <T, VB : ViewBinding> RecyclerView.setup(
    adapter: CommonAdapter<T, VB>,
    orientation: Int = RecyclerView.VERTICAL
): CommonAdapter<T, VB> {
    layoutManager = LinearLayoutManager(context, orientation, false)
    this.adapter = adapter
    return adapter
}
