package com.geotab.AOA.helpers

import com.geotab.AOA.helpers.IOXHelper.Companion.getIOXTopicListMessage
import com.geotab.ioxproto.IoxMessaging.IoxToGo
import org.testng.AssertJUnit.assertEquals
import org.testng.AssertJUnit.assertTrue
import org.testng.annotations.Test

internal class IOXHelperTest{
    @Test
    fun testAll(){
        println("getIOXTopicListMessage: ${getIOXTopicListMessage()}")
    }

    @Test
    fun testGetIOXTopicListMessage(){
        val actual = getIOXTopicListMessage()
        assertEquals("testGetIOXTopicListMessage", actual.msgCase, IoxToGo.MsgCase.PUB_SUB)
        assertTrue("testGetIOXTopicListMessage", actual.pubSub.hasListAvailTopics())
    }
}
