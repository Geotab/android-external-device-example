package com.geotab.AOA

import com.geotab.ioxproto.IoxMessaging

interface IOXListener {
    fun onIOXReceived(message : IoxMessaging.IoxFromGo)
    fun onStatusUpdate(message : String)
    fun onUpdateHOSText(dataHOS : HOSData)
    fun onPassthroughReceived(message : String)
}