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
import java.util.Locale;
import java.util.Objects;
import java.util.stream.IntStream;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.WindowManager;
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


public class Sandbox extends AppCompatActivity  implements IOXListener
{
	private Spinner mSpinner;
	private AccessoryControl mAccessoryControl;
	private static final String TAG = Sandbox.class.getSimpleName();	// Used for error logging
	List<TopicsDataModel> dataModels = new ArrayList<>();;
	private static TopicsRecyclerAdapter mTopicAdapter;
	MainBinding binding = null;
	private ThirdParty.State mInterfaceStatus = ThirdParty.State.SEND_SYNC;
	// Called when the activity is initialized
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		binding = MainBinding.inflate(getLayoutInflater());
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

		mAccessoryControl = new AccessoryControl(this, this);

		dataModels.add(new TopicsDataModel("Empty", -1));
		DisplayMetrics metrics = this.getResources().getDisplayMetrics();
		mTopicAdapter = new TopicsRecyclerAdapter(dataModels, metrics, (item, index) ->
		{
			Log.d(TAG, "onItemClick: " + item);
			if (mInterfaceStatus!= ThirdParty.State.IDLE){
				showToast("IOX is not ready!");
				return null;
			}
			if (item.getId()>0){
				if(item.getSubscribed() != TopicsDataModel.SubscriptionStatus.SUBSCRIBED){
					item.setSubscribed(TopicsDataModel.SubscriptionStatus.SUBSCRIBING);
					protoBufSubscribeToTopic(item.getId());
				}else{
					item.setSubscribed(TopicsDataModel.SubscriptionStatus.UNSUBSCRIBING);
					protoBufUnsubscribeToTopic(item.getId());
					Log.d(TAG, "protoBufUnsubscribeToTopic: " + item);
				}

			}
			mTopicAdapter.notifyItemChanged(index);
			return null;
		});
		binding.topicsList.setAdapter(mTopicAdapter);
		binding.topicsList.setLayoutManager(new LinearLayoutManager(this));

		binding.btnIoxTopics.setOnClickListener(v -> protoGetAvailableTopics());
		binding.btnGetSubs.setOnClickListener(v -> protoGetSubscribedTopics());
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
		OpenStatus status = mAccessoryControl.open(this);
		if (status == OpenStatus.CONNECTED){
			showToast("Connected (OnResume)");
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		}
		else if (status != OpenStatus.REQUESTING_PERMISSION && status != OpenStatus.NO_ACCESSORY)
			showToast("Error: " + status);
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

					OpenStatus status = mAccessoryControl.open(Sandbox.this, accessory);
					if (status == OpenStatus.CONNECTED){
						showToast("Connected (onReceive)");
						getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
					} else {
						showToast("Error: " + status);
					}
				}
				else
				{
					Log.i(TAG, "Permission NOT Granted");
				}
			}
			else if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action))
			{
				showToast("Detached");
				mAccessoryControl.close();
				getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
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

	void protoBufUnsubscribeToTopic(int topic){
		byte[] ioxMessage = IOXHelper.Companion.getIOXUnsubscribeToTopicMessage(topic).toByteArray();
		sendThirdPartyProtoBuf(ioxMessage);
	}

	void sendThirdPartyProtoBuf(byte[] ioxMessage){
		if (mInterfaceStatus == ThirdParty.State.IDLE){
			byte[] abMessage = new byte[ioxMessage.length];
			System.arraycopy(ioxMessage, 0, abMessage, 0, ioxMessage.length);
			mAccessoryControl.sendThirdParty(ThirdParty.PROTOBUF_DATA_PACKET, abMessage);
		}else{
			Log.e(TAG, "IOX is not ready!");
			showToast("IOX is not ready!");
		}

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
	// Function : showToast
	// Purpose : Run a toast message.
	// Parameters : [I] toast: The string to display
	// Return : None
	// Notes : None
	// -----------------------------------------------------------------------------
	public void showToast(final String sToast)
	{
		Log.i(TAG, sToast);
		Toast DisplayMessage = Toast.makeText(getApplicationContext(), sToast, Toast.LENGTH_SHORT);
		DisplayMessage.show();
	}

	// Displays the set location on Google maps
	private void showLocationOnMap()
	{		
		TextView textView;
		textView = findViewById(R.id.Latitude);
		String sLatitude = textView.getText().toString();
		textView = findViewById(R.id.Logitude);
		String sLongitude = textView.getText().toString();
				
		// Pass the location to Google maps
		String geoCode = "geo:0,0?q=" + sLatitude + "," + sLongitude + "(HOS Location)";
		Intent sendLocationToMap = new Intent(Intent.ACTION_VIEW, Uri.parse(geoCode));
		startActivity(sendLocationToMap);
	}

	@Override
	public void onIOXReceived(@NonNull IoxMessaging.IoxFromGo message) {
		Log.d(TAG, "onIOXReceived:\n MsgCase:"+message.getMsgCase()+"\nmessage:"
				+message.toString());
		if (message.getMsgCase() == IoxMessaging.IoxFromGo.MsgCase.PUB_SUB){
			if (message.getPubSub().hasTopicInfoList()){
				updateTopicsInfoList(message.getPubSub().getTopicInfoList().getTopicsList());
			}
			if (message.getPubSub().hasTopicList()){
				updateTopicSubscriptions(message.getPubSub().getTopicList().getTopicsList());
			}
			if (message.getPubSub().hasPub()){
				updateTopic(message.getPubSub().getPub());
			}
			if (message.getPubSub().hasSubAck()){
				updateSubAck(message.getPubSub().getSubAck());
			}
			if (message.getPubSub().hasClearSubsAck()){
				updateClearAllSubAck(message.getPubSub().getClearSubsAck());
			}
		}
	}

	public void updateClearAllSubAck(IoxMessaging.ClearSubsAck clearSubsAck){
		if(clearSubsAck.getResult() == IoxMessaging.ClearSubsAck.Result.CLEAR_SUBS_ACK_RESULT_SUCCESS){
			Log.d(TAG, "updatClearSubAck!");
			//Todo:Clear all subscription status to unsubscribed!
		}
	}
	public void updateSubAck(IoxMessaging.SubAck subAck){
		if(subAck.getResult() == IoxMessaging.SubAck.Result.SUB_ACK_RESULT_SUCCESS ||
				subAck.getResult() == IoxMessaging.SubAck.Result.SUB_ACK_RESULT_TOPIC_ALREADY_SUBBED){
			updateSubscriptionStatus(subAck.getTopic(), true);
		}else{
			updateSubscriptionStatus(subAck.getTopic(), false);
		}
	}

	public void updateTopic(IoxMessaging.Publish publish) {
		switch (publish.getValueCase()) {
			case BOOL_VALUE:
				updateDataSet(publish.getTopic(), Boolean.toString(publish.getBoolValue()));
				break;
			case INT_VALUE:
				updateDataSet(publish.getTopic(), Integer.toString(publish.getIntValue()));
				break;
			case UINT_VALUE:
				//Java doesn't have any unsigned value!
				updateDataSet(publish.getTopic(), Integer.toString(publish.getUintValue()));
				break;
			case FLOAT_VALUE:
				String strResultFloat = String.format(Locale.getDefault(),
						"%.3f", publish.getFloatValue());
				updateDataSet(publish.getTopic(), strResultFloat);
				break;
			case STRING_VALUE:
				updateDataSet(publish.getTopic(), publish.getStringValue());
				break;
			case VEC3_VALUE:
				String strResultVec3 = String.format(Locale.getDefault(),
						"[%.2f,%.2f,%.2f]",
						publish.getVec3Value().getX(),
						publish.getVec3Value().getY(),
						publish.getVec3Value().getZ());
				updateDataSet(publish.getTopic(), strResultVec3);
				break;
			case GPS_VALUE:
				String strResultGPS = String.format(Locale.getDefault(),
						"[%.4f,%.4f,%.1f]",
						publish.getGpsValue().getLatitude(),
						publish.getGpsValue().getLongitude(),
						publish.getGpsValue().getSpeed());
				updateDataSet(publish.getTopic(), strResultGPS);
				break;
			case VALUE_NOT_SET:
				break;
		}
	}

	public void updateSubscriptionStatus(IoxMessaging.Topic topic,
										 boolean acked){
		if (acked) {
			int index = getIndexByTopicName(topic);
			if (index >= 0) {
				Log.d(TAG, "updateDataSet: found index: " + index);
				TopicsDataModel data = dataModels.get(index);

				if (data.getSubscribed() == TopicsDataModel.SubscriptionStatus.SUBSCRIBING) {
					data.setSubscribed(TopicsDataModel.SubscriptionStatus.SUBSCRIBED);
				}
				if (data.getSubscribed() == TopicsDataModel.SubscriptionStatus.UNSUBSCRIBING) {
					data.setSubscribed(TopicsDataModel.SubscriptionStatus.UNSUBSCRIBED);
				}
				dataModels.set(index, data);
				mTopicAdapter.notifyItemChanged(index);

			} else {
				Log.e(TAG, "updateDataSet: didn't find any index!");
			}
		}else{
			Log.e(TAG, "updateDataSet: failed to unsubscribe!");
		}
	}
	public void updateDataSet(IoxMessaging.Topic topic, String prompt){

		int index = getIndexByTopicName(topic);
			if(index >= 0){
				Log.d(TAG, "updateDataSet: found index: "+ index + " prompt: "+prompt);
				TopicsDataModel data = dataModels.get(index);
				data.setDataText(prompt);
				//We assume when data arrives, the topic was subscribed!
				if (data.getSubscribed() != TopicsDataModel.SubscriptionStatus.SUBSCRIBED){
					data.setSubscribed(TopicsDataModel.SubscriptionStatus.SUBSCRIBED);
				}
				data.incrementCounter();
				dataModels.set(index,data);
				mTopicAdapter.notifyItemChanged(index);
			}else{
				Log.e(TAG, "updateDataSet: didn't find any index!");
			}
	}

	int getIndexByTopicName(IoxMessaging.Topic topic){
		return IntStream.range(0, dataModels.size())
				.filter(i -> Objects.equals(dataModels.get(i).getName(), topic.name()))
				.findFirst()
				.orElse(-1);
	}

	@SuppressLint("NotifyDataSetChanged")
	public void updateTopicsInfoList(List<IoxMessaging.TopicInfo> topics){
		if (topics.size()>0){
			dataModels.clear();
			for (IoxMessaging.TopicInfo topicInfo : topics) {
				IoxMessaging.Topic mmTopic =topicInfo.getTopic();
				if (mmTopic.getNumber()>0){
					dataModels.add(new TopicsDataModel(mmTopic.name(),mmTopic.getNumber()));
					Log.d(TAG, "add: "+mmTopic.name());
				}
			}
			mTopicAdapter.notifyDataSetChanged();
		}
	}

	public void updateTopicSubscriptions(List<IoxMessaging.Topic> topics){
		if (dataModels.size()>0){
			for (IoxMessaging.Topic topic : topics) {
				if (topic.getNumber()>0){
					int index = getIndexByTopicName(topic);
					if (index >= 0){
						TopicsDataModel data = dataModels.get(index);
						data.setSubscribed(TopicsDataModel.SubscriptionStatus.SUBSCRIBED);
						dataModels.set(index, data);
						mTopicAdapter.notifyItemChanged(index);
						Log.d(TAG, "Subscribed: "+topic.name());
					}
				}
			}
		}
	}

	@Override
	public void onStatusUpdate(@NonNull String message) {
		Toast DisplayMessage = Toast.makeText(this, message, Toast.LENGTH_SHORT);
		DisplayMessage.show();
	}

	@Override
	public void onUpdateHOSText(@NonNull HOSData dataHOS) {
		if(binding != null){
			binding.DateTime.setText(dataHOS.sDateTime);
			binding.Latitude.setText(Float.toString(dataHOS.Latitude));
			binding.Logitude.setText(Float.toString(dataHOS.Longitude));
			binding.Speed.setText(Integer.toString(dataHOS.iRoadSpeed));
			binding.RPM.setText(Integer.toString(dataHOS.iRPM));
			binding.Odometer.setText(Integer.toString(dataHOS.iOdometer));
			binding.Status.setText(dataHOS.sStatus);
			binding.TripOdometer.setText(Integer.toString(dataHOS.iTripOdometer));
			binding.EngineHours.setText(Integer.toString(dataHOS.iEngineHours));
			binding.TripDuration.setText(Integer.toString(dataHOS.iTripDuration));
			binding.VehicleId.setText(Integer.toString(dataHOS.iVehicleId));
			binding.DriverId.setText(Integer.toString(dataHOS.iDriverId));

		}
	}

	@Override
	public void onPassthroughReceived(@NonNull String message) {
		if(binding != null){
			binding.PassthroughReceived.setText(message);
		}
	}

	@Override
	public void onConnected() {
		Log.d(TAG, "onConnected!");
	}

	@Override
	public void onIOXStateChanged(@NonNull ThirdParty.State state) {
		mInterfaceStatus = state;
		binding.textIoxStatus.setText(state.name());
		binding.btnIoxTopics.setEnabled(state == ThirdParty.State.IDLE);
		binding.btnGetSubs.setEnabled(state == ThirdParty.State.IDLE);
	}
}
