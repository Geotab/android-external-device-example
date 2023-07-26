package com.geotab.AOA.helpers

import com.geotab.ioxproto.IoxMessaging

class IOXHelper {
    companion object{
        fun getIOXTopicListMessage(): ByteArray{
            val ioxMessage = IoxMessaging.IoxToGo.newBuilder().setPubSub(
                IoxMessaging.PubSubToGo
                    .newBuilder()
                    .clearListAvailTopics()
                    .build()
            )
                .build()
                .toByteArray()
            return ioxMessage
        }

        fun ByteArray.toHexString() = asUByteArray().joinToString("") {
            it.toString(16).padStart(2, '0')
        }
    }
}