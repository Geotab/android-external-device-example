package com.geotab.AOA

import android.graphics.Color
import android.graphics.Typeface
import java.util.Objects

class TopicsDataModel(
    var name: String,
    var id: Int,
    var subscribed: SubscriptionStatus = SubscriptionStatus.UNKNOWN
) {

    var dataText: String = ""
    var counter = 0

    constructor(name: String, id: Int) : this(name, id, SubscriptionStatus.UNKNOWN)

    enum class SubscriptionStatus(
        val statusText: String,
        val color: Int,
        val typeface: Int,
        val layoutElv: Float
    ) {
        SUBSCRIBED(
            "Subscribed", Color.argb(255, 0, 150, 0),
            Typeface.BOLD, 4.0f
        ),
        UNSUBSCRIBED(
            "Unsubscribed", Color.RED,
            Typeface.ITALIC, 2.0f
        ),
        SUBSCRIBING(
            "Subscribing...", Color.argb(255, 200, 200, 0),
            Typeface.ITALIC, 2.0f
        ),
        UNSUBSCRIBING(
            "Unsubscribing...", Color.argb(255, 200, 200, 0),
            Typeface.ITALIC, 2.0f
        ),
        UNKNOWN(
        "Unknown", Color.GRAY,
        Typeface.ITALIC, 1.0f
        )
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
