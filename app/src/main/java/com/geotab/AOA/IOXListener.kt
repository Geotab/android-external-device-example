package com.geotab.AOA

import com.geotab.ioxproto.IoxMessaging

interface IOXListener {
    fun onIOXReceived(message : IoxMessaging.IoxFromGo)
}