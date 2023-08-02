package com.geotab.AOA

import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView


class TopicsRecyclerAdapter(private val dataSet: List<TopicsDataModel>, private val metrics: DisplayMetrics,
                            private val listener: (TopicsDataModel, Int) -> Unit) :
    RecyclerView.Adapter<TopicsRecyclerAdapter.ViewHolder>() {
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val lblSub: TextView
        val valueText: TextView
        val counterText: TextView
        val statusText: TextView
        val topicLayout: LinearLayout
        init {
            // Define click listener for the ViewHolder's View
            lblSub = view.findViewById(R.id.lbl_sub)
            valueText = view.findViewById(R.id.topic_data)
            statusText  = view.findViewById(R.id.topic_sub_status)
            counterText  = view.findViewById(R.id.topic_counter)
            topicLayout  = view.findViewById(R.id.topic_sub_layout)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.topics_list_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        viewHolder.lblSub.text = dataSet[position].name
        viewHolder.lblSub.setTypeface(null, dataSet[position].subscribed.typeface);
        viewHolder.itemView.setOnClickListener { listener(dataSet[position], position) }
        val valueTextStr ="Value: ${dataSet[position].dataText}"
        viewHolder.valueText.text =valueTextStr
        val counterTextStr ="Counter: ${dataSet[position].counter}"
        viewHolder.counterText.text =counterTextStr
        viewHolder.statusText.text= dataSet[position].subscribed.statusText
        viewHolder.statusText.setTextColor(dataSet[position].subscribed.color)
        viewHolder.statusText.setTypeface(null, dataSet[position].subscribed.typeface);
        viewHolder.topicLayout.elevation = convertDipsToPixels(dataSet[position].subscribed.layoutElv)
    }

    private fun convertDipsToPixels(dp: Float): Float {
        return dp * metrics.density
    }
    override fun getItemCount() = dataSet.size

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getItemViewType(position: Int): Int {
        return position
    }

}
