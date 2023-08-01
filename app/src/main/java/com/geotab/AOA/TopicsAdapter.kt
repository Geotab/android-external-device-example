package com.geotab.AOA

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox

class TopicsAdapter(dataSet: ArrayList<TopicsDataModel>, context: Context?) :
    ArrayAdapter<TopicsDataModel?>(
        context!!, R.layout.topics_list_item, dataSet.toList()
    ), View.OnClickListener {
    override fun getViewTypeCount(): Int {
        return count
    }

    override fun getItemViewType(position: Int): Int {
        return position
    }

    private class ViewModel {
        // TextView txtNa
        // //Todo: add topic data counter!
        var chkSub: CheckBox? = null
    }

    override fun onClick(v: View) {
        val position = v.tag as Int
        val item = getItem(position)
        Log.d("TopicsAdapter", "id: " + v.id)
        when (v.id) {
            R.id.chk_sub -> Log.d("TopicsAdapter", "TopicInfoList: btn_sub $item")
        }
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var mmConvertView = convertView
        val dataModel = getItem(position)
        val viewHolder: ViewModel
        if (mmConvertView == null) {
            viewHolder = ViewModel()
            val inflater = LayoutInflater.from(context)
            mmConvertView = inflater.inflate(R.layout.topics_list_item, parent, false)
            viewHolder.chkSub = mmConvertView.findViewById(R.id.chk_sub)
            mmConvertView.tag = viewHolder
        } else {
            viewHolder = mmConvertView.tag as ViewModel
        }
        viewHolder.chkSub!!.setOnClickListener(this)
        viewHolder.chkSub!!.text = dataModel!!.name
        viewHolder.chkSub!!.tag = position
        return mmConvertView!!
    }
}
