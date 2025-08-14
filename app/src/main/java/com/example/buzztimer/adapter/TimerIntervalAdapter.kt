package com.example.buzztimer.adapter

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.buzztimer.R
import com.example.buzztimer.databinding.ItemTimerIntervalBinding
import com.example.buzztimer.model.TimerInterval
import java.util.Collections

/**
 * Adapter for displaying and managing timer intervals with drag-to-reorder functionality
 */
class TimerIntervalAdapter(
    private val intervals: MutableList<TimerInterval>,
    private val onEditClick: (position: Int, interval: TimerInterval) -> Unit,
    private val onDeleteClick: (position: Int) -> Unit
) : RecyclerView.Adapter<TimerIntervalAdapter.ViewHolder>() {

    private var itemTouchHelper: ItemTouchHelper? = null
    private var recyclerView: RecyclerView? = null

    inner class ViewHolder(val binding: ItemTimerIntervalBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            // Set up drag handle touch listener
            binding.ivDragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    itemTouchHelper?.startDrag(this)
                }
                false
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTimerIntervalBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val interval = intervals[position]
        
        // Set interval name (if available) or default name
        val intervalName = interval.name?.takeIf { it.isNotBlank() } ?: "Interval ${position + 1}"
        holder.binding.tvIntervalName.text = intervalName
        
        // Set interval time
        holder.binding.tvIntervalTime.text = holder.itemView.context.getString(
            R.string.interval_format,
            interval.minutes,
            interval.seconds
        )

        // Set click listeners
        holder.binding.btnEdit.setOnClickListener {
            onEditClick(holder.adapterPosition, interval)
        }

        holder.binding.btnDelete.setOnClickListener {
            onDeleteClick(holder.adapterPosition)
        }
    }

    override fun getItemCount() = intervals.size

    /**
     * Sets up drag-to-reorder functionality for the RecyclerView
     */
    fun attachToRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = recyclerView
        
        val touchHelperCallback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                source: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = source.adapterPosition
                val toPos = target.adapterPosition
                
                // Swap items in the list
                Collections.swap(intervals, fromPos, toPos)
                notifyItemMoved(fromPos, toPos)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // Not using swipe functionality
            }
        }

        itemTouchHelper = ItemTouchHelper(touchHelperCallback).apply {
            attachToRecyclerView(recyclerView)
        }
    }

    fun addInterval(interval: TimerInterval) {
        intervals.add(interval)
        notifyItemInserted(intervals.size - 1)
        recyclerView?.scrollToPosition(intervals.size - 1)
    }

    fun updateInterval(position: Int, interval: TimerInterval) {
        if (position in intervals.indices) {
            intervals[position] = interval
            notifyItemChanged(position)
        }
    }

    fun removeInterval(position: Int) {
        if (position in intervals.indices) {
            intervals.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    fun getIntervals(): List<TimerInterval> = intervals.toList()
}
