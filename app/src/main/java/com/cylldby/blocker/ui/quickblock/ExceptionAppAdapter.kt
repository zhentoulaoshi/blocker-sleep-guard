package com.cylldby.blocker.ui.quickblock

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cylldby.blocker.R

class ExceptionAppAdapter(
    private val onRemove: (String) -> Unit
) : ListAdapter<String, ExceptionAppAdapter.VH>(DIFF) {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvLabel: TextView = view.findViewById(R.id.tvExceptionLabel)
        val btnRemove: ImageButton = view.findViewById(R.id.btnRemoveException)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_exception_app, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val pkg = getItem(position)
        val pm = holder.itemView.context.packageManager
        val label = try {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (_: Exception) { pkg }
        holder.tvLabel.text = label
        holder.btnRemove.setOnClickListener { onRemove(pkg) }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<String>() {
            override fun areItemsTheSame(a: String, b: String) = a == b
            override fun areContentsTheSame(a: String, b: String) = a == b
        }
    }
}
