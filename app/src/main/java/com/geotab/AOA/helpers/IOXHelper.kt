package com.geotab.AOA.helpers

import com.geotab.ioxproto.IoxMessaging
import com.geotab.ioxproto.IoxMessaging.IoxToGo
import com.geotab.ioxproto.IoxMessaging.PubSubToGo
import com.geotab.ioxproto.IoxMessaging.Subscribe
import com.google.protobuf.Empty

class IOXHelper {

    companion object {
        fun getIOXTopicListMessage(): IoxToGo {
            return IoxToGo.newBuilder()
                .setPubSub(PubSubToGo.newBuilder().setListAvailTopics(Empty.getDefaultInstance()))
                .build()
        }

        fun getIOXSubscribedTopicListMessage(): IoxToGo {
            return IoxToGo.newBuilder()
                .setPubSub(PubSubToGo.newBuilder().setListSubs(Empty.getDefaultInstance()))
                .build()
        }

        fun getIOXSubscribeToTopicMessage(topic : Int): IoxToGo {
            return IoxToGo.newBuilder()
                .setPubSub(
                    PubSubToGo
                        .newBuilder()
                        .setSub(Subscribe.newBuilder().setTopicValue(topic)))
                .build()
        }

        fun getIOXUnsubscribeToTopicMessage(topic : Int): IoxToGo {
            return IoxToGo.newBuilder()
                .setPubSub(
                    PubSubToGo
                        .newBuilder()
                        .setUnsub(IoxMessaging.Unsubscribe.newBuilder().setTopicValue(topic)))
                .build()
        }

        fun getIOXUnsubscribeAllMessage(): IoxToGo {
            return IoxToGo.newBuilder()
                .setPubSub(
                    PubSubToGo
                        .newBuilder().setClearSubs(Empty.getDefaultInstance()))
                .build()
        }


        fun ByteArray.toHexString() = asUByteArray().joinToString("") {
            it.toString(16).padStart(2, '0')
        }
    }
}
