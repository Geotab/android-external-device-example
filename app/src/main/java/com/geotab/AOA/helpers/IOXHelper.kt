package com.geotab.AOA.helpers

import com.geotab.ioxproto.IoxMessaging
import com.geotab.ioxproto.IoxMessaging.IoxToGo
import com.geotab.ioxproto.IoxMessaging.PubSubToGo
import com.geotab.ioxproto.IoxMessaging.Subscribe
import com.google.protobuf.Empty

class IOXHelper {

    companion object {
        public fun getIOXTopicListMessage(): IoxToGo {
            return IoxToGo.newBuilder()
                .setPubSub(PubSubToGo.newBuilder().setListAvailTopics(Empty.getDefaultInstance()))
                .build()
        }

        public fun getIOXSubscribeToGPSMessage(): IoxToGo {
            return IoxToGo.newBuilder()
                .setPubSub(PubSubToGo.newBuilder().setSub(Subscribe.newBuilder().setTopicValue(
                    IoxMessaging.Topic.TOPIC_GEAR_VALUE)))
                .build()
        }

        fun ByteArray.toHexString() = asUByteArray().joinToString("") {
            it.toString(16).padStart(2, '0')
        }
    }
}
