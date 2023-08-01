package com.geotab.AOA

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.recyclerview.widget.RecyclerView


class TopicsRcyclerAdapter(private val dataSet: List<TopicsDataModel>) :
    RecyclerView.Adapter<TopicsRcyclerAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        //val textView: TextView
        val chkSub: CheckBox
        init {
            // Define click listener for the ViewHolder's View
            chkSub = view.findViewById(R.id.chk_sub)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.topics_list_item, parent, false)

        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        viewHolder.chkSub.text = dataSet[position].name
        viewHolder.chkSub.isChecked = dataSet[position].subscribed
    }

    override fun getItemCount() = dataSet.size

}
