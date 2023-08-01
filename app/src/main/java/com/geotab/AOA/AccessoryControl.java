/*****************************************************************************
 *
 * Copyright (C) 2017, Geotab Inc.
 * Portions Copyright(C) 2011, Embedded Artists AB
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

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicBoolean;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.widget.TextView;

public class AccessoryControl
{
	public static final String ACTION_USB_PERMISSION = "com.geotab.AOA.MainActivity.action.USB_PERMISSION";

	// Note: If changed, this also needs to be updated in accessory_filter.xml
	private static final String ACC_MANUF = "Geotab";	// Expected manufacturer name
	private static final String ACC_MODEL = "IOX USB";	// Expected model name

	private static final String TAG = AccessoryControl.class.getSimpleName();	// Used for error logging

	public enum OpenStatus
	{
		CONNECTED, REQUESTING_PERMISSION, UNKNOWN_ACCESSORY, NO_ACCESSORY, NO_PARCEL
	}

	private final Lock mLock = new ReentrantLock();
	private final Condition mReceiverEnded = mLock.newCondition();

	private boolean mfPermissionRequested, mfConnectionOpen;

	private final UsbManager mUSBManager;
	private final Context mContext;
	private ParcelFileDescriptor mParcelFileDescriptor;
	private FileOutputStream mOutputStream;
	private FileInputStream mInputStream;
	private Receiver mReceiver;
	private ThirdParty mThirdParty;

	// Constructor
	public AccessoryControl(Context context)
	{
		mfPermissionRequested = false;
		mfConnectionOpen = false;
		mThirdParty = null;

		mContext = context;
		mUSBManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
	}

	// Requests permissions to access the accessory
	public OpenStatus open()
	{
		if (mfConnectionOpen)
			return OpenStatus.CONNECTED;

		UsbAccessory[] accList = mUSBManager.getAccessoryList();	// The accessory list only returns 1 entry
		if (accList != null && accList.length > 0)
		{
			// If permission has been granted, try to establish the connection
			if (mUSBManager.hasPermission(accList[0]))
				return open(accList[0]);

			// If not, request permission
			if (!mfPermissionRequested)
			{
				Log.i(TAG, "Requesting USB permission");

				PendingIntent permissionIntent = PendingIntent.getBroadcast(mContext, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
				mUSBManager.requestPermission(accList[0], permissionIntent);
				mfPermissionRequested = true;

				return OpenStatus.REQUESTING_PERMISSION;
			}
		}

		return OpenStatus.NO_ACCESSORY;
	}

	// Try to establish a connection to the accessory
	public OpenStatus open(UsbAccessory accessory)
	{
		if (mfConnectionOpen)
			return OpenStatus.CONNECTED;

		// Check if the accessory is supported by this app
		if (!ACC_MANUF.equals(accessory.getManufacturer()) || !ACC_MODEL.equals(accessory.getModel()))
		{
			Log.i(TAG, "Unknown accessory: " + accessory.getManufacturer() + ", " + accessory.getModel());
			return OpenStatus.UNKNOWN_ACCESSORY;
		}

		// Open read/write streams for the accessory
		mParcelFileDescriptor = mUSBManager.openAccessory(accessory);
		
		if (mParcelFileDescriptor != null)
		{
			FileDescriptor fd = mParcelFileDescriptor.getFileDescriptor();
			mOutputStream = new FileOutputStream(fd);
			mInputStream = new FileInputStream(fd);
			
			mfConnectionOpen = true;

			mReceiver = new Receiver();
			new Thread(mReceiver).start();			// Run the receiver as a separate thread

			return OpenStatus.CONNECTED;
		}

		Log.i(TAG, "Couldn't get any ParcelDescriptor");
		return OpenStatus.NO_PARCEL;
	}

	// End and shutdown the communication with the accessory
	public void appIsClosing()
	{
		if (!mfConnectionOpen)
			return;

		mReceiver.close();

		mLock.lock();
		try
		{
			// Wait up to 100ms for the receiver thread to gracefully close the link
			mReceiverEnded.await(100, TimeUnit.MILLISECONDS);
		}
		catch (InterruptedException e)
		{
			Log.w(TAG, "Exception in disconnect timeout", e);
		}
		finally
		{
			mLock.unlock();
		}
	}

	// Stop and clean up the connection to the accessory
	public void close()
	{
		if (!mfConnectionOpen)
			return;

		mfPermissionRequested = false;
		mfConnectionOpen = false;

		// End the receiver thread
		mReceiver.close();
		Log.i(TAG, "Receiver Thread closed");
		
		// Close the data streams 
		try
		{
			mInputStream.close();
			Log.i(TAG, "Input Stream closed");
		}
		catch (IOException e)
		{
			Log.w(TAG, "Exception when closing Input Stream", e);
		}
		
		try
		{
			mOutputStream.close();
			Log.i(TAG, "Output Stream closed");
		}
		catch (IOException e)
		{
			Log.w(TAG, "Exception when closing Output Stream", e);
		}
		
		try
		{
			mParcelFileDescriptor.close();
			Log.i(TAG, "File Descriptor closed");
		}
		catch (IOException e)
		{
			Log.w(TAG, "Exception when closing File Descriptor", e);
		}
	}

	// Send a command to the accessory
	public void write(byte[] abData)
	{
		if (!mfConnectionOpen)
			return;

		try
		{
			// Lock the output stream for the write operation
			synchronized (mOutputStream)
			{
				mOutputStream.write(abData);
			}
		}
		catch (IOException e)
		{
			Log.w(TAG, "Exception writing to output stream", e);
			close();
		}
	}

	// A new thread that receives messages from the accessory
	private class Receiver implements Runnable
	{
		private final AtomicBoolean fRunning = new AtomicBoolean(true);

		// Constructor
		Receiver()
		{
			mThirdParty = new ThirdParty(AccessoryControl.this, mContext);
		}

		public void run()
		{
			int iNumberOfBytesRead = 0;
			byte[] abBuffer = new byte[512];	// max is [16384]

			Log.i(TAG, "Receiver thread started");

			try
			{
				while (fRunning.get())
				{
					// Note: Read blocks until one byte has been read, the end of the source stream is detected or an exception is thrown
					iNumberOfBytesRead = mInputStream.read(abBuffer);

					if (fRunning.get() && (iNumberOfBytesRead > 0))
					{
						byte[] abMessage = new byte[iNumberOfBytesRead];
						System.arraycopy(abBuffer, 0, abMessage, 0, abMessage.length);

						mThirdParty.RxMessage(abMessage);

						StringBuffer sDisplay = convertToString(abMessage);
						updateTextOnUI(sDisplay);
					}
				}
			}
			catch (IOException e)
			{
				Log.w(TAG, "Exception reading input stream", e);
				close();
			}

			mLock.lock();
			try
			{
				mReceiverEnded.signal();
			}
			finally
			{
				mLock.unlock();
			}
			
			Log.i(TAG, "Receiver thread ended");
		}

		// Shutdown the receiver and third party threads
		public void close()
		{
			fRunning.set(false);
			
			if (mThirdParty != null)
			{
				mThirdParty.close();
				mThirdParty = null;
			}
		}
	};

	// Pass data to the third party layer
	public void sendThirdParty(byte bType, byte[] abData)
	{
		if (mThirdParty == null){
			Log.e(TAG, "sendThirdParty: mThirdParty is null!");
			return;
		}


		mThirdParty.TxMessage(bType, abData);
	}

	// Converts a byte array to a string
	private static StringBuffer convertToString(byte[] abIn)
	{
		StringBuffer sData = new StringBuffer();

		for (byte b : abIn) {
			if ((b >> 4) == 0)
				sData.append('0');

			sData.append(Integer.toHexString(b & 0xFF).toUpperCase(Locale.US)).append(" ");
		}

		return sData;
	}

	// Update text on the UI thread from another calling thread
	private void updateTextOnUI(final StringBuffer sDisplay)
	{
		final Activity activity = (Activity) mContext;

		activity.runOnUiThread(new Runnable()
		{
			public void run()
			{
				TextView PassthroughReceived = (TextView) activity.findViewById(R.id.PassthroughReceived);
				PassthroughReceived.setText(sDisplay);
			}
		});
	}
}
