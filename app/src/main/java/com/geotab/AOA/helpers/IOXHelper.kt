package com.geotab.AOA.helpers

import com.geotab.ioxproto.IoxMessaging.IoxToGo
import com.geotab.ioxproto.IoxMessaging.PubSubToGo
import com.google.protobuf.Empty

class IOXHelper {
    companion object{
        fun getIOXTopicListMessage(): IoxToGo {
            return IoxToGo
                .newBuilder()
                .setPubSub(PubSubToGo.newBuilder().setListAvailTopics(Empty.getDefaultInstance()))
                .build()
        }

        fun ByteArray.toHexString() = asUByteArray().joinToString("") {
            it.toString(16).padStart(2, '0')
        }
    }
}