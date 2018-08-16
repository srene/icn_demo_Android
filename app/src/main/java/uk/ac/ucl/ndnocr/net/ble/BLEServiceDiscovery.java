/*
 * Copyright (c) 2016 Vladimir L. Shabanov <virlof@gmail.com>
 *
 * Licensed under the Underdark License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://underdark.io/LICENSE.txt
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.ac.ucl.ndnocr.net.ble;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

//import uk.ac.ucl.ndnocr.data.ContentAdvertisement;
import uk.ac.ucl.ndnocr.data.NdnOcrService;
import uk.ac.ucl.ndnocr.utils.G;

import static android.content.Context.BLUETOOTH_SERVICE;
import static uk.ac.ucl.ndnocr.net.ble.Constants.SERVICE_UUID;

//import uk.ac.ucl.ndnocr.data.UbiCDNService;

//import uk.ac.ucl.ndnocr.net.bluetooth.pairing.BtPairer;

public class BLEServiceDiscovery {

	private static final String TAG = "BLEServiceDiscovery";
	// Uppercase.

	private Context context;

	/* Bluetooth API */
	private BluetoothManager mBluetoothManager;
	private BluetoothAdapter mBluetoothAdapter;
	private BluetoothGatt mBluetoothGatt;
	private int mConnectionState = STATE_DISCONNECTED;

	private static final int STATE_DISCONNECTED = 0;
	private static final int STATE_CONNECTING = 1;
	private static final int STATE_CONNECTED = 2;

	public final static String ACTION_GATT_CONNECTED =
			"com.example.bluetooth.le.ACTION_GATT_CONNECTED";
	public final static String ACTION_GATT_DISCONNECTED =
			"com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
	public final static String ACTION_GATT_SERVICES_DISCOVERED =
			"com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
	public final static String ACTION_DATA_AVAILABLE =
			"com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
	public final static String EXTRA_DATA =
			"com.example.bluetooth.le.EXTRA_DATA";

	//private ContentAdvertisement ca;
	private Handler mHandler;
    private Handler sHandler;
    private boolean mScanning;
	private static final long SCAN_PERIOD = 2000;
	public static final long RETRY = 15000;

	Set<BluetoothDevice> results;

	private boolean started=false;
	//public final static UUID UUID_HEART_RATE_MEASUREMENT[] = {UUID.fromString("00001805-0000-1000-8000-00805f9b34fb")};
	//		UUID.fromString(SampleGattAttributes.HEART_RATE_MEASUREMENT);
	//public static UUID NDN_OCR_SERVICE = UUID.fromString("00001805-0000-1000-8000-00805f9b34fb");
	//public final static UUID SERVICES[] = {NDN_OCR_SERVICE};

	private NdnOcrService service;

	private BluetoothLeScanner mLEScanner;
	//boolean isWorking = false;
	//boolean jobCancelled = false;
	boolean mInitialized = false;
	int serverstate = BluetoothProfile.STATE_DISCONNECTED;

	BluetoothDevice device=null;
	BroadcastReceiver mBroadcastReceiver;
	public BLEServiceDiscovery(
			Context context)
	{
		mBluetoothManager = (BluetoothManager) context.getSystemService(BLUETOOTH_SERVICE);
		mBluetoothAdapter = mBluetoothManager.getAdapter();
		this.context = context;
		mHandler = new Handler();
        sHandler = new Handler();
		this.service = (NdnOcrService) context;
		mLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
	//	mRegisteredDevices = new HashSet<>();
		results = new HashSet<BluetoothDevice>();
        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                G.Log(TAG,"Broadcast received "+intent);
                switch (intent.getAction()) {
                    case GattServerCallback.BLESERVER_STATE:
                        serverstate = intent.getIntExtra("state",0);
                        if(serverstate==BluetoothProfile.STATE_CONNECTED)mLEScanner.stopScan(mScanCallback);
                        //startAdvertising();
                        break;
                }
            }
        };

    }


    public void start()
	{

		if(started) {
			stop();
		}

		results.clear();
		serverstate = BluetoothProfile.STATE_DISCONNECTED;
		mConnectionState = STATE_DISCONNECTED;

		if (!mBluetoothAdapter.isEnabled()) {
			G.Log(TAG, "Bluetooth is currently disabled...enabling ");
			mBluetoothAdapter.enable();
		} else {
			G.Log(TAG, "Bluetooth enabled...starting services");

		}

        context.registerReceiver(mBroadcastReceiver, GattServerCallback.getIntentFilter());
		started=true;
		scanLeDevice();

    }

	public void stop()
	{
		// Listener queue.
		if(started) {
			G.Log(TAG,"Stop");
			started=false;
			mLEScanner.stopScan(mScanCallback);
			mLEScanner.flushPendingScanResults(mScanCallback);
			mHandler.removeCallbacksAndMessages(null);
			sHandler.removeCallbacksAndMessages(null);
            context.unregisterReceiver(mBroadcastReceiver);
            close();
		}

	}


	private void scanLeDevice() {

		if(!started){
			G.Log(TAG,"Not started cancelling");
			return;
		}

        mConnectionState=STATE_DISCONNECTED;
		ScanSettings settings = new ScanSettings.Builder()
				.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
				.build();
		List<ScanFilter> filters = new ArrayList<ScanFilter>();
			// Stops scanning after a pre-defined scan period.
		ScanFilter scanFilter = new ScanFilter.Builder()
				.setServiceUuid(SERVICE_UUID)
				.build();
		filters.add(scanFilter);


		G.Log(TAG, "Nougat start scan");


		mHandler.postDelayed(new Runnable() {
			@Override
			public void run() {
				mScanning = false;
				mLEScanner.stopScan(mScanCallback);
				if(started&&mConnectionState==STATE_DISCONNECTED)tryConnection();
                //mLEScanner.stopScan(mScanCallback);
                mLEScanner.flushPendingScanResults(mScanCallback);
				try{ Thread.sleep(RETRY);}catch (Exception e){}
				//if(mConnectionState==STATE_DISCONNECTED)scanLeDevice();
				//if(started)scanLeDevice();
				if(started){
					//stop();
					//start(ca);
					start();
				}
			}
		}, SCAN_PERIOD);

		if(mConnectionState==STATE_DISCONNECTED&&!mScanning)
		{
			mLEScanner.startScan(filters, settings, mScanCallback);
			mScanning = true;
		}

	}

	private ScanCallback mScanCallback = new ScanCallback() {
		@Override
		public void onScanResult(int callbackType, ScanResult result) {
			if(!results.contains(result.getDevice()))results.add(result.getDevice());
		}

		@Override
		public void onBatchScanResults(List<ScanResult> results) {
			for (ScanResult sr : results) {
				G.Log(TAG,"ScanResult - Results", sr.toString());
			}
		}

		@Override
		public void onScanFailed(int errorCode) {
			G.Log(TAG,"Scan Failed", "Error Code: " + errorCode);
			try{ Thread.sleep(SCAN_PERIOD);}catch (Exception e){}
			scanLeDevice();
		}
	};

	public void tryConnection(){
		//String address = android.provider.Settings.Secure.getString(context.getContentResolver(), "bluetooth_address");
		for(BluetoothDevice res: results){
			//G.Log(TAG,"Tryconnection "+results.size()+" "+address+" "+res.getAddress()+" "+address.hashCode()+" "+res.getAddress().hashCode());
			if(mBluetoothAdapter.getAddress().hashCode()>res.getAddress().hashCode()){
				//G.Log("Connect to "+res.getName()+" "+res.getAddress());
				if(connect(res.getAddress())){
					results.remove(res);
					break;
				}
			}

		}
	}
	/**
	 * Connects to the GATT server hosted on the Bluetooth LE device.
	 *
	 * @param address The device address of the destination device.
	 *
	 * @return Return true if the connection is initiated successfully. The connection result
	 *         is reported asynchronously through the
	 *         {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
	 *         callback.
	 */
	public boolean connect(final String address) {
		if (mBluetoothAdapter == null || address == null) {
			G.Log(TAG, "BluetoothAdapter not initialized or unspecified address.");
			return false;
		}

		device = mBluetoothAdapter.getRemoteDevice(address);
		if (device == null) {
			G.Log(TAG, "Device not found.  Unable to connect.");
			return false;
		}
		// We want to directly connect to the device, so we are setting the autoConnect
		// parameter to false.
		//mHandler.removeCallbacksAndMessages(null);
		mBluetoothGatt = device.connectGatt(context, false, mGattCallback);
		G.Log(TAG, "Trying to create a new connection to "+address);
		mConnectionState = STATE_CONNECTING;
		return true;
	}

	/**
	 * Disconnects an existing connection or cancel a pending connection. The disconnection result
	 * is reported asynchronously through the
	 * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
	 * callback.
	 */
	public void disconnect() {
		if (mBluetoothAdapter == null || mBluetoothGatt == null) {
			G.Log(TAG, "BluetoothAdapter not initialized");
			return;
		}
		mBluetoothGatt.disconnect();
		mInitialized = false;
	}

	/**
	 * After using a given BLE device, the app must call this method to ensure resources are
	 * released properly.
	 */
	public void close() {
		if (mBluetoothGatt == null) {
			return;
		}
		mBluetoothGatt.close();
		mBluetoothGatt = null;
	}


	// Implements callback methods for GATT events that the app cares about.  For example,
	// connection change and services discovered.
	private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
		@Override
		public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
			String intentAction;
			if (newState == BluetoothProfile.STATE_CONNECTED&&mConnectionState!=BluetoothProfile.STATE_CONNECTED) {
				//gatt.requestMtu(512);
				intentAction = ACTION_GATT_CONNECTED;
				mConnectionState = STATE_CONNECTED;
				//broadcastUpdate(intentAction);
				G.Log(TAG, "Connected to GATT server: "+gatt.getDevice().getAddress());
				// Attempts to discover services after successful connection.
				//G.Log(TAG, "Attempting to start service discovery:" );
				mBluetoothGatt.discoverServices();

			} else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
				intentAction = ACTION_GATT_DISCONNECTED;
				mConnectionState = STATE_DISCONNECTED;
				G.Log(TAG, "Disconnected from GATT server.");
				//scanLeDevice();
				//broadcastUpdate(intentAction);
                //stop();
                //start(ca);
				if(started)tryConnection();
			}
		}

		@Override
		public void onServicesDiscovered(BluetoothGatt gatt, int status) {

			super.onServicesDiscovered(gatt, status);

			if (status != BluetoothGatt.GATT_SUCCESS) {
				G.Log(TAG,"Device service discovery unsuccessful, status " + status);
				return;
			}

			List<BluetoothGattCharacteristic> matchingCharacteristics = BluetoothUtils.findCharacteristics(gatt);
			if (matchingCharacteristics.isEmpty()) {
				G.Log(TAG,"Unable to find characteristics.");
				return;
			}


			//G.Log(TAG,"Initializing: setting write type and enabling notification");
			for (BluetoothGattCharacteristic characteristic : matchingCharacteristics) {

				characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
				enableCharacteristicNotification(gatt, characteristic);

			}
		}

		@Override
		public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
			super.onMtuChanged(gatt, mtu, status);

			if (status == BluetoothGatt.GATT_SUCCESS) {
				//G.Log(TAG,"Supported mtu "+mtu);
				if(mInitialized)sendMessage();
			}
		}

		@Override
		public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				G.Log(TAG,"Characteristic onDescriptorWrite success");
				//mClientActionListener.log("Descriptor written successfully: " + descriptor.getUuid().toString());
				// mClientActionListener.initializeTime();
			} else {
				G.Log(TAG,"Characteristic onDescriptorWrite not success");
				// mClientActionListener.logError("Descriptor write unsuccessful: " + descriptor.getUuid().toString());
			}
		}

		@Override
		public void onCharacteristicRead(BluetoothGatt gatt,
										 BluetoothGattCharacteristic characteristic,
										 int status) {
			G.Log(TAG, "onCharacteristicRead received: " + status);
			if (status == BluetoothGatt.GATT_SUCCESS) {
				//broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
			}
		}

		@Override
		public void onCharacteristicChanged(BluetoothGatt gatt,
											BluetoothGattCharacteristic characteristic) {
			G.Log(TAG, "onCharacteristicChanged received: "+characteristic.getValue());
			//if(Arrays.equals(new byte[]{0x01},characteristic.getValue()))
                sHandler.removeCallbacksAndMessages(null);
				readCharacteristic(characteristic);

			//broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
		}
	};


	private void enableCharacteristicNotification(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
		boolean characteristicWriteSuccess = gatt.setCharacteristicNotification(characteristic, true);
		if (characteristicWriteSuccess) {
			//G.Log(TAG,"Characteristic notification set successfully for " + characteristic.getUuid().toString());
			if (BluetoothUtils.isEchoCharacteristic(characteristic)) {
				mInitialized = true;
				gatt.requestMtu(512);
			} /*else if (BluetoothUtils.isTimeCharacteristic(characteristic)) {
				enableCharacteristicConfigurationDescriptor(gatt, characteristic);
			}*/
		} else {
			G.Log(TAG,"Characteristic notification set failure for " + characteristic.getUuid().toString());
		}
	}


	// Sometimes the Characteristic does not have permissions, and instead its Descriptor holds them
	// See https://www.bluetooth.com/specifications/gatt/viewer?attributeXmlFile=org.bluetooth.descriptor.gatt.client_characteristic_configuration.xml
	/*private void enableCharacteristicConfigurationDescriptor(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {

		List<BluetoothGattDescriptor> descriptorList = characteristic.getDescriptors();
		BluetoothGattDescriptor descriptor = BluetoothUtils.findClientConfigurationDescriptor(descriptorList);
		if (descriptor == null) {
			G.Log(TAG,"Unable to find Characteristic Configuration Descriptor");
			return;
		}

		descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
		boolean descriptorWriteInitiated = gatt.writeDescriptor(descriptor);
		if (descriptorWriteInitiated) {
			G.Log(TAG,"Characteristic Configuration Descriptor write initiated: " + descriptor.getUuid().toString());
		} else {
			G.Log(TAG,"Characteristic Configuration Descriptor write failed to initiate: " + descriptor.getUuid().toString());
		}
	}*/

	private void readCharacteristic(BluetoothGattCharacteristic characteristic) {

        byte[] messageBytes = characteristic.getValue();
        String message = StringUtils.stringFromBytes(messageBytes);
        //G.Log(TAG, "Read: " + StringUtils.byteArrayInHexFormat(messageBytes));

        if (message == null) {
            G.Log(TAG, "Unable to convert bytes to string");
            return;
        }
        //G.Log(TAG, "Received message: " + message);

        if (Arrays.equals(new byte[]{0x00}, messageBytes)){
            disconnect();
            //listener.linkNetworkSameDiscovered(device.getAddress());
        }else
		    service.linkNetworkDiscovered(message);
        //stop();
		//stop();
		//close();
	}

	private void sendMessage() {
		if (mConnectionState != STATE_CONNECTED || !mInitialized) {
			G.Log(TAG,"Not initialized.");
			return;
		}

		BluetoothGattCharacteristic characteristic = BluetoothUtils.findEchoCharacteristic(mBluetoothGatt);
		if (characteristic == null) {
			G.Log(TAG,"Unable to find echo characteristic.");
			disconnect();
			return;
		}


		//byte[] messageBytes = ca.getFilterBytes();
		byte[] messageBytes = new byte[]{0x00};

		G.Log(TAG,"Sending message: " + new String(messageBytes) + " " + messageBytes.length);

		if (messageBytes.length == 0) {
			G.Log(TAG,"Unable to convert message to bytes");
			return;
		}

		characteristic.setValue(messageBytes);
		mInitialized=false;

		boolean success = mBluetoothGatt.writeCharacteristic(characteristic);

        sHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                //start(ca);
				start();
                //if(started)scanLeDevice();
            }
        }, RETRY*3);

		/*if (success) {
			G.Log(TAG,"Wrote: " + StringUtils.byteArrayInHexFormat(messageBytes));
		} else {
			G.Log(TAG,"Failed to write data");
		}*/
		if(!success)G.Log(TAG,"Failed to write data");
	}


	//endregion
} // BtServiceDiscovery

