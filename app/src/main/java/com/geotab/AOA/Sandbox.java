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

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import com.geotab.AOA.AccessoryControl.OpenStatus;
import com.geotab.AOA.databinding.MainBinding;
import com.geotab.AOA.helpers.IOXHelper;
import com.geotab.ioxproto.IoxMessaging;

public class Sandbox extends Activity
{
	private Spinner mSpinner;
	private AccessoryControl mAccessoryControl;
	private static final String TAG = Sandbox.class.getSimpleName();	// Used for error logging
	ArrayList<TopicsDataModel> dataModels;
	private static TopicsAdapter adapter;
	// Called when the activity is initialized
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		MainBinding binding = MainBinding.inflate(getLayoutInflater());
		setContentView(binding.getRoot());

		// Register a receiver for permission and accessory detached messages
		IntentFilter filter = new IntentFilter(AccessoryControl.ACTION_USB_PERMISSION);
		filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
		registerReceiver(receiver, filter);

		// Button actions
		binding.Send.setOnClickListener(v -> writePassthrough());
		binding.SendToMap.setOnClickListener(v -> showLocationOnMap());

		// Received Text View
		binding.PassthroughReceived.setMovementMethod(new ScrollingMovementMethod());
		binding.hosLayout.setVisibility(View.VISIBLE);
		binding.pubSubLayout.setVisibility(View.GONE);
		// Selectable HOS message list
		List<String> sDispalyedList = new ArrayList<String>();
		for (int i = 0; i < ThirdParty.THIRD_PARTY_MESSAGE_DEFINEs.length; i++)
		{
			sDispalyedList.add(ThirdParty.THIRD_PARTY_MESSAGE_DEFINEs[i].Name);
		}
		mSpinner = binding.MessageSpinner;
		ArrayAdapter<String> SpinnerAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, sDispalyedList);
		SpinnerAdapter.setDropDownViewResource(android.R.layout.select_dialog_singlechoice);
		mSpinner.setAdapter(SpinnerAdapter);
		mSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				if (parent.getItemAtPosition(position).toString().equals("PROTOBUF PUB/SUB")){
					binding.hosLayout.setVisibility(View.GONE);
					binding.pubSubLayout.setVisibility(View.VISIBLE);
					//Todo:
					Log.d(TAG, "Switch to PROTOBUF PUB/SUB Mode");
				}else{
					binding.hosLayout.setVisibility(View.VISIBLE);
					binding.pubSubLayout.setVisibility(View.GONE);
				}
			}
			@Override
			public void onNothingSelected(AdapterView<?> parent) {
				Log.d(TAG, "setOnItemClickListener onNothingSelected!");
			}});

		mAccessoryControl = new AccessoryControl(this);
		dataModels= new ArrayList<>();
		try {
			for (IoxMessaging.Topic topic : IoxMessaging.Topic.values()) {
				Log.d(TAG, "TopicInfoList: "+topic.name());
				if (topic.getNumber()>0){
					dataModels.add(new TopicsDataModel(topic.name(),topic.getNumber(), false));
				}
			}
		}catch (Exception ex){
			Log.d(TAG, "TopicInfoList: "+ex.getMessage());
		}
		adapter= new TopicsAdapter(dataModels,getApplicationContext());
		binding.topicsList.setAdapter(adapter);
		binding.topicsList.setOnItemClickListener((parent, view, position, id) -> {
			TopicsDataModel dataModel= dataModels.get(position);
			Log.d(TAG, "onItemClick: "+dataModel);
		});
		binding.btnIoxTopics.setOnClickListener(v -> protoGetAvailableTopics());
		binding.btnGetSubs.setOnClickListener(v ->
				protoGetSubscribedTopics());
		binding.btnSubGps.setOnClickListener(v ->
				protoBufSubscribeToTopic(IoxMessaging.Topic.TOPIC_GEAR_VALUE));
	}

	// Runs when the activity goes to the background
	@Override
	public void onPause()
	{
		mAccessoryControl.appIsClosing();
		mAccessoryControl.close();
		super.onPause();
	}

	// Runs when the activity resumes from the background or after it is created
	@Override
	public void onResume()
	{
		super.onResume();
		OpenStatus status = mAccessoryControl.open();
		if (status == OpenStatus.CONNECTED)
			showToastFromThread("Connected (OnResume)");
		else if (status != OpenStatus.REQUESTING_PERMISSION && status != OpenStatus.NO_ACCESSORY)
			showToastFromThread("Error: " + status);
	}

	// Runs as the last call before the activity is shutdown
	@Override
	protected void onDestroy()
	{
		unregisterReceiver(receiver);
		super.onDestroy();
	}

	// Listens for permission and accessory detached messages (registered in onCreate)
	private final BroadcastReceiver receiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			String action = intent.getAction();
			Log.d(TAG, "BroadcastReceiver action:"+ action);
			// Check the reason the receiver was called
			if (AccessoryControl.ACTION_USB_PERMISSION.equals(action))
			{
				UsbAccessory accessory = (UsbAccessory) intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);

				if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
				{
					Log.i(TAG, "Permission Granted");

					OpenStatus status = mAccessoryControl.open(accessory);
					if (status == OpenStatus.CONNECTED)
						showToastFromThread("Connected (onReceive)");
					else
						showToastFromThread("Error: " + status);
				}
				else
				{
					Log.i(TAG, "Permission NOT Granted");
				}
			}
			else if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action))
			{
				showToastFromThread("Detached");
				mAccessoryControl.close();
			}
		}
	};

	// Sends a data message to the USB
	private void writePassthrough()
	{
		byte[] abCommand = ThirdParty.THIRD_PARTY_MESSAGE_DEFINEs[mSpinner.getSelectedItemPosition()].Command;
		byte bMessageType = ThirdParty.THIRD_PARTY_MESSAGE_DEFINEs[mSpinner.getSelectedItemPosition()].MessageType;

		EditText InputBox = (EditText) findViewById(R.id.PassthroughWrite);
		String sIn = InputBox.getText().toString();
		byte[] abData = ConvertToHex(sIn);

		if (bMessageType != 0)
		{
			if (abCommand != null)
			{
				// Append the selected command
				byte[] abMessage = new byte[abCommand.length + abData.length];
				System.arraycopy(abCommand, 0, abMessage, 0, abCommand.length);
				System.arraycopy(abData, 0, abMessage, abCommand.length, abData.length);
				mAccessoryControl.sendThirdParty(bMessageType, abMessage);
			}
			else
			{
				if (bMessageType != ThirdParty.PROTOBUF_DATA_PACKET){
					// Send the data and message type directly
					mAccessoryControl.sendThirdParty(bMessageType, abData);
				}
			}
		}
		else
		{
			// Bypass the command and write data directly
			bMessageType = abData[0];
			byte[] abMessage = new byte[abData.length - 1];
			System.arraycopy(abData, 1, abMessage, 0, abMessage.length);
			mAccessoryControl.sendThirdParty(bMessageType, abMessage);
		}
	}


	void protoGetSubscribedTopics(){
		byte[] ioxMessage = IOXHelper.Companion.getIOXSubscribedTopicListMessage().toByteArray();
		sendThirdPartyProtoBuf(ioxMessage);
	}

	void protoGetAvailableTopics(){
		byte[] ioxMessage = IOXHelper.Companion.getIOXTopicListMessage().toByteArray();
		sendThirdPartyProtoBuf(ioxMessage);
	}

	void protoBufSubscribeToTopic(int topic){
		byte[] ioxMessage = IOXHelper.Companion.getIOXSubscribeToTopicMessage(topic).toByteArray();
		sendThirdPartyProtoBuf(ioxMessage);
	}

	void sendThirdPartyProtoBuf(byte[] ioxMessage){
		byte[] abMessage = new byte[ioxMessage.length];
		System.arraycopy(ioxMessage, 0, abMessage, 0, ioxMessage.length);
		mAccessoryControl.sendThirdParty(ThirdParty.PROTOBUF_DATA_PACKET, abMessage);
	}

	// Converts a string to a byte array
	private static byte[] ConvertToHex(String s)
	{
		int iLen = s.length();
		byte[] abHex = new byte[iLen / 2];

		for (int i = 0; i < iLen; i += 2)
		{
			abHex[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
		}

		return abHex;
	}

	// -----------------------------------------------------------------------------
	// Function : showToastFromThread
	// Purpose : Run a toast message on the UI thread from another calling thread
	// Parameters : [I] toast: The string to display
	// Return : None
	// Notes : None
	// -----------------------------------------------------------------------------
	public void showToastFromThread(final String sToast)
	{
		Log.i(TAG, sToast);

		runOnUiThread(new Runnable()
		{
			public void run()
			{
				Toast DisplayMessage = Toast.makeText(getApplicationContext(), sToast, Toast.LENGTH_SHORT);
				DisplayMessage.show();
			}
		});
	}

	// Displays the set location on Google maps
	private void showLocationOnMap()
	{		
		TextView textView;
		textView = (TextView) findViewById(R.id.Latitude);
		String sLatitude = textView.getText().toString();
		textView = (TextView) findViewById(R.id.Logitude);
		String sLogitude = textView.getText().toString();
				
		// Pass the location to Google maps
		String geoCode = "geo:0,0?q=" + sLatitude + "," + sLogitude + "(HOS Location)";
		Intent sendLocationToMap = new Intent(Intent.ACTION_VIEW, Uri.parse(geoCode));
		startActivity(sendLocationToMap);
	}
}
