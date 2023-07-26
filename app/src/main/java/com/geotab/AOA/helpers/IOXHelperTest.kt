package com.geotab.AOA.helpers

import com.geotab.AOA.helpers.IOXHelper.Companion.getIOXTopicListMessage
import com.geotab.AOA.helpers.IOXHelper.Companion.toHexString
import org.testng.annotations.Test


internal class IOXHelperTest{
    @Test
    fun testAll(){
        println("getIOXTopicListMessage: ${getIOXTopicListMessage().toHexString()}")
    }
}