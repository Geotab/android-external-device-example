/*****************************************************************************
 *
 * Copyright (C) 2017, Geotab Inc.
 *
 ******************************************************************************
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *****************************************************************************/
package com.geotab.AOA;

import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicBoolean;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import com.geotab.ioxproto.IoxMessaging;
import com.google.protobuf.InvalidProtocolBufferException;

class ThirdPartyMessage {
    public String Name;
    public byte MessageType;
    public byte[] Command;

    ThirdPartyMessage(String sName, byte messageType, byte[] abCommand) {
        Name = sName;
        MessageType = messageType;
        Command = abCommand;
    }
}

public class ThirdParty {
    private static final String TAG = ThirdParty.class.getSimpleName();    // Used for error logging

    private static final HOSData mHOSdata = new HOSData();

    private static final byte MESSAGE_HANDSHAKE = 1;
    private static final byte MESSAGE_ACK = 2;
    private static final byte MESSAGE_GO_DEVICE_DATA = 0x21;
    private static final byte MESSAGE_GO_TO_IOX = 0x26;
    private static final byte MESSAGE_CONFIRMATION = (byte) 0x81;
    private static final byte MESSAGE_STATUS_DATA = (byte) 0x80;
    private static final byte TP_FREE_FORMAT_DATA = (byte) 0x82;
    private static final byte TP_DEVICE_INFO_RECEIVED = (byte) 0x83;
    private static final byte TP_HOS_ACK = (byte) 0x84;
    static final byte PROTOBUF_DATA_PACKET = (byte) 0x8c;
    private static final byte MESSAGE_SYNC = 0x55;
    private static final byte[] HOS_ENHANCED_ID_WITH_ACK = new byte[]{0x2D, 0x10, 0x00, 0x00};

    static final ThirdPartyMessage[] THIRD_PARTY_MESSAGE_DEFINEs = new ThirdPartyMessage[]
            {
                    new ThirdPartyMessage("-BYPASS-", (byte) 0, null),
                    new ThirdPartyMessage("STATUS: OUTSIDE TEMPERATURE", MESSAGE_STATUS_DATA, new byte[]{0x35, 0x00}),        // 53
                    new ThirdPartyMessage("STATUS: ENGINE WARNING LIGHT", MESSAGE_STATUS_DATA, new byte[]{0x24, 0x00}),        // 36
                    new ThirdPartyMessage("STATUS: PARK BRAKE", MESSAGE_STATUS_DATA, new byte[]{0x31, 0x00}),                // 49
                    new ThirdPartyMessage("FREE FORMAT", TP_FREE_FORMAT_DATA, null),
                    new ThirdPartyMessage("DEVICE INFO", TP_DEVICE_INFO_RECEIVED, null),
                    new ThirdPartyMessage("HOS ACK", TP_HOS_ACK, null),
                    new ThirdPartyMessage("PROTOBUF PUB/SUB", PROTOBUF_DATA_PACKET, null),
            };

    private final Lock mLock = new ReentrantLock();
    private final Condition mEvent = mLock.newCondition();

    private byte[] mabMessage;
    private boolean mfAckReceived, mfHandshakeReceived, mfMessageToSend;

    private AccessoryControl mAccessoryControl;
    private StateMachine mStateMachine;
    private final Handler mHandler;
    private IOXListener mIOXListener;

    private enum State {
        SEND_SYNC, WAIT_FOR_HANDSHAKE, SEND_CONFIRMATION, PRE_IDLE, IDLE, WAIT_FOR_ACK
    }

    // Constructor
    public ThirdParty(AccessoryControl accessory, Context context, IOXListener ioxListener) {
        mfHandshakeReceived = false;
        mfAckReceived = false;
        mfMessageToSend = false;
        mAccessoryControl = accessory;
        mHandler = new Handler(context.getMainLooper());
        mIOXListener = ioxListener;
        mStateMachine = new StateMachine();
        new Thread(mStateMachine).start();        // Run as a separate thread
    }

    // State machine to handle the third party protocol
    private class StateMachine implements Runnable {
        private State eState = State.SEND_SYNC;
        private AtomicBoolean fRunning = new AtomicBoolean(true);

        public void run() {
            Log.i(TAG, "Third party SM started");

            while (fRunning.get()) {
                mLock.lock();        // The lock is needed for await and atomic access to flags/buffers

                try {
                    switch (eState) {
                        case SEND_SYNC: {
                            byte[] abMessage = new byte[]{MESSAGE_SYNC};
                            mAccessoryControl.write(abMessage);
                            eState = State.WAIT_FOR_HANDSHAKE;
                            break;
                        }
                        case WAIT_FOR_HANDSHAKE: {
                            // Waits for the handshake message or resends sync every 1s
                            mEvent.await(1000, TimeUnit.MILLISECONDS);

                            if (mfHandshakeReceived) {
                                eState = State.SEND_CONFIRMATION;
                            } else {
                                eState = State.SEND_SYNC;
                            }
                            break;
                        }
                        case SEND_CONFIRMATION: {
                            byte[] abMessage = BuildMessage(MESSAGE_CONFIRMATION, HOS_ENHANCED_ID_WITH_ACK);
                            mAccessoryControl.write(abMessage);
                            showStatusMsg("HOS Connected");
                            eState = State.PRE_IDLE;
                            break;
                        }
                        case PRE_IDLE: {
                            mfHandshakeReceived = false;
                            mfAckReceived = false;
                            mfMessageToSend = false;
                            eState = State.IDLE;
                            break;
                        }
                        case IDLE: {
                            // Sleep and wait for a handshake or a message to send
                            mEvent.await();

                            if (mfHandshakeReceived) {
                                eState = State.SEND_CONFIRMATION;
                            } else if (mfMessageToSend) {
                                mAccessoryControl.write(mabMessage);
                                eState = State.WAIT_FOR_ACK;
                            }
                            break;
                        }
                        case WAIT_FOR_ACK: {
                            // Wait for the ack or reset after 5s
                            mEvent.await(5000, TimeUnit.MILLISECONDS);

                            if (mfAckReceived) {
                                eState = State.PRE_IDLE;
                            } else {
                                eState = State.SEND_SYNC;
                            }
                            break;
                        }
                        default: {
                            eState = State.SEND_SYNC;
                            break;
                        }
                    }

                } catch (InterruptedException e) {
                    Log.w(TAG, "Exception during await", e);
                } finally {
                    mLock.unlock();
                }
            }
        }

        // Stop the thread
        public void close() {
            Log.i(TAG, "Shutting down third party SM");

            mLock.lock();
            try {
                fRunning.set(false);
                mfHandshakeReceived = false;
                mfAckReceived = false;
                mfMessageToSend = false;
                mEvent.signal();
            } finally {
                mLock.unlock();
            }
        }
    }

    // Signal the state machine to stop
    public void close() {
        if (mStateMachine != null)
            mStateMachine.close();
    }

    // Encapsulate a message to be sent
    public void TxMessage(byte bType, byte[] abData) {
        Log.d(TAG, "TxMessage:" + bType + ", abData.length:" + abData.length);
        mLock.lock();
        try {
            mabMessage = BuildMessage(bType, abData);
            mfMessageToSend = true;
            mEvent.signal();
        } finally {
            mLock.unlock();
        }
    }

    // Checks if a received message matches the expected third party format
    public void RxMessage(byte[] abData) {
        // Check length
        if (abData == null || abData.length < 6) {
            Log.e(TAG, "RxMessage: Bad Data length!");
            return;
        }

        // Check structure
        byte bSTX = abData[0];
        byte bLength = abData[2];
        byte bETX = abData[abData.length - 1];

        if (bSTX != 0x02 || bETX != 0x03) {
            Log.e(TAG, "RxMessage: Bad Data format!");
            return;
        }


        // Check checksum
        byte[] abChecksum = new byte[]{abData[abData.length - 3], abData[abData.length - 2]};
        byte[] abCalcChecksum = CalcChecksum(abData, bLength + 3);

        if (!Arrays.equals(abChecksum, abCalcChecksum)) {
            Log.e(TAG, "RxMessage: Bad Data Checksum!");
            return;
        }

        byte bType = abData[1];

        switch (bType) {
            case MESSAGE_HANDSHAKE:
                mLock.lock();
                try {
                    mfHandshakeReceived = true;
                    mEvent.signal();
                } finally {
                    mLock.unlock();
                }
                break;

            case MESSAGE_ACK:
                mLock.lock();
                try {
                    mfAckReceived = true;
                    mEvent.signal();
                } finally {
                    mLock.unlock();
                }
                break;

            case MESSAGE_GO_DEVICE_DATA:
                ExtractHOSData(abData);

                byte[] abAck = new byte[]{};
                mabMessage = BuildMessage(TP_HOS_ACK, abAck);
                mAccessoryControl.write(mabMessage);
                break;
            case MESSAGE_GO_TO_IOX:
                try {
                    byte[] mDate = new byte[abData.length - 6];
                    System.arraycopy(abData, 3, mDate, 0, mDate.length);
                    IoxMessaging.IoxFromGo ioxFromGoMsg = IoxMessaging.IoxFromGo.parseFrom(mDate);
                    Log.d(TAG, "RxMessage: MESSAGE_GO_TO_IOX MsgCase:"
                            + ioxFromGoMsg.getMsgCase());
                    if (mIOXListener != null && mHandler != null) {
                        mHandler.post(() -> {
                            mIOXListener.onIOXReceived(ioxFromGoMsg);
                        });
                    }
                } catch (InvalidProtocolBufferException e) {
                    Log.e(TAG, "RxMessage: Failed to decode the protobuf data\n"
                            + e.getMessage());
                }
                break;
        }
    }

    // Assemble a third party message
    private byte[] BuildMessage(byte bType, byte[] abData) {
        byte[] abMessage = new byte[abData.length + 6];

        abMessage[0] = 0x02;
        abMessage[1] = bType;
        abMessage[2] = (byte) abData.length;

        System.arraycopy(abData, 0, abMessage, 3, abData.length);

        int iLengthUpToChecksum = abData.length + 3;
        byte[] abCalcChecksum = CalcChecksum(abMessage, iLengthUpToChecksum);
        System.arraycopy(abCalcChecksum, 0, abMessage, iLengthUpToChecksum, 2);

        abMessage[abMessage.length - 1] = 0x03;

        return abMessage;
    }

    // Calculate the Fletcher's checksum over the given bytes
    private byte[] CalcChecksum(byte[] abData, int iLength) {
        byte[] abChecksum = new byte[]{0x00, 0x00};

        for (int i = 0; i < iLength; i++) {
            abChecksum[0] += abData[i];
            abChecksum[1] += abChecksum[0];
        }

        return abChecksum;
    }

    public void ExtractHOSData(byte[] abData) {
        synchronized (mHOSdata) {
            ByteBuffer abConvert;

            byte[] abDateTime = new byte[4];
            System.arraycopy(abData, 3, abDateTime, 0, abDateTime.length);
            abConvert = ByteBuffer.wrap(abDateTime).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            int iDateTime = abConvert.getInt();
            Calendar c = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
            c.clear();
            c.set(2002, Calendar.JANUARY, 1);        // (Units given in seconds since Jan 1, 2002)
            c.add(Calendar.SECOND, iDateTime);
            SimpleDateFormat dataFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US);
            mHOSdata.sDateTime = dataFormat.format(c.getTime());

            byte[] abLatitude = new byte[4];
            System.arraycopy(abData, 7, abLatitude, 0, abLatitude.length);
            abConvert = ByteBuffer.wrap(abLatitude).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            int iLatitude = abConvert.getInt();
            mHOSdata.Latitude = (float) iLatitude / 10000000;    // (Units given in 10^-7)

            byte[] abLogitude = new byte[4];
            System.arraycopy(abData, 11, abLogitude, 0, abLogitude.length);
            abConvert = ByteBuffer.wrap(abLogitude).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            int iLogitude = abConvert.getInt();
            mHOSdata.Logitude = (float) iLogitude / 10000000;    // (Units given in 10^-7)

            mHOSdata.iRoadSpeed = abData[15];

            byte[] abPRM = new byte[2];
            System.arraycopy(abData, 16, abPRM, 0, abPRM.length);
            abConvert = ByteBuffer.wrap(abPRM).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            mHOSdata.iRPM = abConvert.getShort();
            mHOSdata.iRPM /= 4;            // Convert to RPM (Units given in 0.25)

            byte[] abOdometer = new byte[4];
            System.arraycopy(abData, 18, abOdometer, 0, abOdometer.length);
            abConvert = ByteBuffer.wrap(abOdometer).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            mHOSdata.iOdometer = abConvert.getInt();    // (Units given in 0.1/km)

            byte bStatus = abData[22];
            mHOSdata.sStatus = "";

            if ((bStatus & (1 << 0)) != 0)
                mHOSdata.sStatus += "GPS Latched | ";
            else
                mHOSdata.sStatus += "GPS Invalid | ";

            if ((bStatus & (1 << 1)) != 0)
                mHOSdata.sStatus += "IGN on | ";
            else
                mHOSdata.sStatus += "IGN off | ";

            if ((bStatus & (1 << 2)) != 0)
                mHOSdata.sStatus += "Engine Data | ";
            else
                mHOSdata.sStatus += "No Engine Data | ";

            if ((bStatus & (1 << 3)) != 0)
                mHOSdata.sStatus += "Date/Time Valid | ";
            else
                mHOSdata.sStatus += "Date/Time Invalid | ";

            if ((bStatus & (1 << 4)) != 0)
                mHOSdata.sStatus += "Speed From Engine | ";
            else
                mHOSdata.sStatus += "Speed From GPS | ";

            if ((bStatus & (1 << 5)) != 0)
                mHOSdata.sStatus += "Distance From Engine | ";
            else
                mHOSdata.sStatus += "Distance From GPS | ";

            byte[] abTripOdometer = new byte[4];
            System.arraycopy(abData, 23, abTripOdometer, 0, abTripOdometer.length);
            abConvert = ByteBuffer.wrap(abTripOdometer).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            mHOSdata.iTripOdometer = abConvert.getInt();    // (Units given in 0.1/km)

            byte[] abEngineHours = new byte[4];
            System.arraycopy(abData, 27, abEngineHours, 0, abEngineHours.length);
            abConvert = ByteBuffer.wrap(abEngineHours).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            mHOSdata.iEngineHours = abConvert.getInt();        // Already in units of 0.1h

            byte[] abTripDuration = new byte[4];
            System.arraycopy(abData, 31, abTripDuration, 0, abTripDuration.length);
            abConvert = ByteBuffer.wrap(abTripDuration).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            mHOSdata.iTripDuration = abConvert.getInt();        // Units of seconds

            byte[] abVehicleId = new byte[4];
            System.arraycopy(abData, 35, abVehicleId, 0, abVehicleId.length);
            abConvert = ByteBuffer.wrap(abVehicleId).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            mHOSdata.iVehicleId = abConvert.getInt();

            byte[] abDriverId = new byte[4];
            System.arraycopy(abData, 39, abDriverId, 0, abDriverId.length);
            abConvert = ByteBuffer.wrap(abDriverId).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            mHOSdata.iDriverId = abConvert.getInt();
        }

        updateHOSTextFromThread();
    }

    public synchronized HOSData getHOSData() {
        return mHOSdata;
    }

    // Update text on the UI thread from another thread
    private void updateHOSTextFromThread() {
        HOSData dataHOS = getHOSData();
        if (mHandler != null && mIOXListener != null) {
            mHandler.post(() -> mIOXListener.onUpdateHOSText(dataHOS));
        }
    }

    private void showStatusMsg(final String msg) {
        Log.i(TAG, msg);
        if (mHandler != null && mIOXListener != null) {
            mHandler.post(() -> mIOXListener.onStatusUpdate(msg));
        }
    }
}
