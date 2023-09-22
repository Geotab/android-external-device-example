package com.geotab.AOA;

public class ThirdPartyMessage {
    public String Name;
    public byte MessageType;
    public byte[] Command;

    ThirdPartyMessage(String sName, byte messageType, byte[] abCommand) {
        Name = sName;
        MessageType = messageType;
        Command = abCommand;
    }
}
