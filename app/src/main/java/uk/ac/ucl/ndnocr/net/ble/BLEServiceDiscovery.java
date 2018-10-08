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

import uk.ac.ucl.ndnocr.data.NdnOcrService;
import uk.ac.ucl.ndnocr.utils.G;

import static android.content.Context.BLUETOOTH_SERVICE;
import static uk.ac.ucl.ndnocr.net.ble.Constants.SERVICE_UUID;


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
	private NdnOcrService service;

	private BluetoothLeScanner mLEScanner;
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
                mLEScanner.flushPendingScanResults(mScanCallback);
				try{ Thread.sleep(RETRY);}catch (Exception e){}

				if(started){

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
		for(BluetoothDevice res: results){
			if(mBluetoothAdapter.getAddress().hashCode()>res.getAddress().hashCode()){
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
				mConnectionState = STATE_CONNECTED;
				G.Log(TAG, "Connected to GATT server: "+gatt.getDevice().getAddress());
				// Attempts to discover services after successful connection.
				mBluetoothGatt.discoverServices();

			} else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
				intentAction = ACTION_GATT_DISCONNECTED;
				mConnectionState = STATE_DISCONNECTED;
				G.Log(TAG, "Disconnected from GATT server.");
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
				if(mInitialized)sendMessage();
			}
		}

		@Override
		public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				G.Log(TAG,"Characteristic onDescriptorWrite success");
			} else {
				G.Log(TAG,"Characteristic onDescriptorWrite not success");
			}
		}

		@Override
		public void onCharacteristicRead(BluetoothGatt gatt,
										 BluetoothGattCharacteristic characteristic,
										 int status) {
			G.Log(TAG, "onCharacteristicRead received: " + status);

		}

		@Override
		public void onCharacteristicChanged(BluetoothGatt gatt,
											BluetoothGattCharacteristic characteristic) {
			G.Log(TAG, "onCharacteristicChanged received: "+characteristic.getValue());
                sHandler.removeCallbacksAndMessages(null);
				readCharacteristic(characteristic);

		}
	};


	private void enableCharacteristicNotification(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
		boolean characteristicWriteSuccess = gatt.setCharacteristicNotification(characteristic, true);
		if (characteristicWriteSuccess) {
			if (BluetoothUtils.isEchoCharacteristic(characteristic)) {
				mInitialized = true;
				gatt.requestMtu(512);
			}
		} else {
			G.Log(TAG,"Characteristic notification set failure for " + characteristic.getUuid().toString());
		}
	}


	private void readCharacteristic(BluetoothGattCharacteristic characteristic) {

        byte[] messageBytes = characteristic.getValue();
        String message = StringUtils.stringFromBytes(messageBytes);

        if (message == null) {
            G.Log(TAG, "Unable to convert bytes to string");
            return;
        }

        if (Arrays.equals(new byte[]{0x00}, messageBytes)){
            disconnect();
        }else
		    service.linkNetworkDiscovered(message);

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
				start();
            }
        }, RETRY*3);

		if(!success)G.Log(TAG,"Failed to write data");
	}


	//endregion
} // BtServiceDiscovery

