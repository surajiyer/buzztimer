package com.example.buzztimer.adapter

import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.buzztimer.R
import com.example.buzztimer.databinding.ItemTimerIntervalBinding
import com.example.buzztimer.model.TimerInterval
import java.util.Collections

/**
 * Adapter for displaying and managing timer intervals with drag-to-reorder and menu functionality
 */
class TimerIntervalAdapter(
    private val intervals: MutableList<TimerInterval>,
    private val onEditClick: (position: Int, interval: TimerInterval) -> Unit,
    private val onDeleteClick: (position: Int) -> Unit,
    private val onDuplicateClick: (position: Int, interval: TimerInterval) -> Unit
) : RecyclerView.Adapter<TimerIntervalAdapter.ViewHolder>() {

    private var itemTouchHelper: ItemTouchHelper? = null
    private var recyclerView: RecyclerView? = null
    private var activeIntervalIndex: Int = -1
    private val animators = mutableMapOf<Int, ValueAnimator>()

    inner class ViewHolder(val binding: ItemTimerIntervalBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            // Set up drag listener for the entire card
            binding.mainCard.setOnTouchListener { _, event ->
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

        // Handle active interval highlighting
        if (position == activeIntervalIndex) {
            startPulsingAnimation(holder, position)
        } else {
            stopPulsingAnimation(position)
            // Reset to normal background
            val normalColor = ContextCompat.getColor(holder.itemView.context, R.color.interval_card_background)
            holder.binding.mainCard.setCardBackgroundColor(normalColor)
        }

        // Set menu click listener
        holder.binding.btnMenu.setOnClickListener { view ->
            showPopupMenu(view, position, interval)
        }
    }

    /**
     * Shows the popup menu for interval options
     */
    private fun showPopupMenu(view: android.view.View, position: Int, interval: TimerInterval) {
        val popup = PopupMenu(view.context, view)
        popup.menuInflater.inflate(R.menu.interval_options_menu, popup.menu)
        
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_edit -> {
                    onEditClick(position, interval)
                    true
                }
                R.id.menu_duplicate -> {
                    onDuplicateClick(position, interval)
                    true
                }
                R.id.menu_delete -> {
                    onDeleteClick(position)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    override fun getItemCount() = intervals.size

    /**
     * Sets up drag-to-reorder functionality for the RecyclerView
     */
    fun attachToRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = recyclerView
        
        val touchHelperCallback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0 // No swipe directions
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
                // No swipe functionality - this method is required but not used
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

    fun insertInterval(position: Int, interval: TimerInterval) {
        if (position in 0..intervals.size) {
            intervals.add(position, interval)
            notifyItemInserted(position)
            recyclerView?.scrollToPosition(position)
        }
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
    
    /**
     * Sets the currently active interval index and updates the visual highlighting
     */
    fun setActiveInterval(index: Int) {
        val previousActiveIndex = activeIntervalIndex
        activeIntervalIndex = index
        
        // Update the previous active item (if any)
        if (previousActiveIndex != -1 && previousActiveIndex != index) {
            notifyItemChanged(previousActiveIndex)
        }
        
        // Update the new active item (if valid)
        if (index != -1 && index < intervals.size) {
            notifyItemChanged(index)
        }
    }
    
    /**
     * Clears the active interval highlighting
     */
    fun clearActiveInterval() {
        setActiveInterval(-1)
    }
    
    /**
     * Starts a pulsing animation for the active interval card
     */
    private fun startPulsingAnimation(holder: ViewHolder, position: Int) {
        // Stop any existing animation for this position
        stopPulsingAnimation(position)
        
        val context = holder.itemView.context
        val normalColor = ContextCompat.getColor(context, R.color.interval_card_background)
        val highlightColor = ContextCompat.getColor(context, R.color.primary)
        
        // Create a subtle pulsing effect by blending colors
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = 1500 // 1.5 seconds for a slow, gentle pulse
        animator.repeatCount = ValueAnimator.INFINITE
        animator.repeatMode = ValueAnimator.REVERSE
        
        animator.addUpdateListener { animation ->
            val progress = animation.animatedValue as Float
            // Create a subtle blend between normal and highlight colors
            val blendedColor = blendColors(normalColor, highlightColor, progress * 0.3f) // 30% max blend
            holder.binding.mainCard.setCardBackgroundColor(blendedColor)
        }
        
        animator.start()
        animators[position] = animator
    }
    
    /**
     * Stops the pulsing animation for a specific position
     */
    private fun stopPulsingAnimation(position: Int) {
        animators[position]?.let { animator ->
            animator.cancel()
            animators.remove(position)
        }
    }
    
    /**
     * Blends two colors together with a given ratio
     */
    private fun blendColors(color1: Int, color2: Int, ratio: Float): Int {
        val inverseRatio = 1f - ratio
        val r = (android.graphics.Color.red(color1) * inverseRatio + android.graphics.Color.red(color2) * ratio).toInt()
        val g = (android.graphics.Color.green(color1) * inverseRatio + android.graphics.Color.green(color2) * ratio).toInt()
        val b = (android.graphics.Color.blue(color1) * inverseRatio + android.graphics.Color.blue(color2) * ratio).toInt()
        val a = (android.graphics.Color.alpha(color1) * inverseRatio + android.graphics.Color.alpha(color2) * ratio).toInt()
        return android.graphics.Color.argb(a, r, g, b)
    }
}
