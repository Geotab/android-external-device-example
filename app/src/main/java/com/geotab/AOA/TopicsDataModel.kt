package com.geotab.AOA

import android.graphics.Color
import android.graphics.Typeface
import java.util.Objects

class TopicsDataModel(var name: String,
                      var id: Int,
                      var subscribed: SubscriptionStatus = SubscriptionStatus.UNSUBSCRIBED) {

    var dataText: String = ""
    var counter = 0
    constructor(name: String, id: Int,): this (name, id, SubscriptionStatus.UNSUBSCRIBED)

    enum class SubscriptionStatus(val statusText: String, val color: Int, val typeface: Int) {
        SUBSCRIBED("Subscribed", Color.argb(255, 0, 150, 0),
            Typeface.BOLD),
        UNSUBSCRIBED("Unsubscribed", Color.RED,
            Typeface.ITALIC),
        SUBSCRIBING("Subscribing", Color.argb(255, 150, 150, 0),
            Typeface.ITALIC)
    }

    fun incrementCounter() {
        counter++
    }

    override fun toString(): String {
        return "TopicsDataModel{" +
                "name='" + name + '\'' +
                ", id=" + id +
                ", subscribed=" + subscribed +
                '}'
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false
        val that = o as TopicsDataModel
        return id == that.id && name == that.name
    }

    override fun hashCode(): Int {
        return Objects.hash(name, id, subscribed)
    }
}