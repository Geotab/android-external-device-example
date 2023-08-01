package com.geotab.AOA

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView


class TopicsRecyclerAdapter(private val dataSet: List<TopicsDataModel>) :
    RecyclerView.Adapter<TopicsRecyclerAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val chkSub: CheckBox
        val valueText: TextView
        val counterText: TextView
        val statusText: TextView
        init {
            // Define click listener for the ViewHolder's View
            chkSub = view.findViewById(R.id.chk_sub)
            valueText = view.findViewById(R.id.topic_data)
            statusText  = view.findViewById(R.id.topic_sub_status)
            counterText  = view.findViewById(R.id.topic_counter)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.topics_list_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        viewHolder.chkSub.text = dataSet[position].name
        if (dataSet[position].subscribed == TopicsDataModel.SubscriptionStatus.SUBSCRIBED){
            viewHolder.chkSub.isChecked = true
        }
        val valueTextStr ="Value: ${dataSet[position].dataText}"
        viewHolder.valueText.text =valueTextStr
        val counterTextStr ="Counter: ${dataSet[position].counter}"
        viewHolder.counterText.text =counterTextStr
        viewHolder.statusText.text= dataSet[position].subscribed.statusText
        viewHolder.statusText.setTextColor(dataSet[position].subscribed.color)
        viewHolder.statusText.setTypeface(null, dataSet[position].subscribed.typeface);
    }

    override fun getItemCount() = dataSet.size

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getItemViewType(position: Int): Int {
        return position
    }

}
