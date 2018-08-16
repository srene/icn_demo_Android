package uk.ac.ucl.ndnocr.net.ble;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

//import uk.ac.ucl.ndnocr.data.ContentAdvertisement;
import uk.ac.ucl.ndnocr.data.NdnOcrService;
import uk.ac.ucl.ndnocr.net.wifi.WifiDirectHotSpot;
import uk.ac.ucl.ndnocr.utils.G;

import static uk.ac.ucl.ndnocr.net.ble.Constants.CHARACTERISTIC_ECHO_UUID;
import static uk.ac.ucl.ndnocr.net.ble.Constants.CHARACTERISTIC_TIME_UUID;
import static uk.ac.ucl.ndnocr.net.ble.Constants.CLIENT_CONFIGURATION_DESCRIPTOR_UUID;
import static uk.ac.ucl.ndnocr.net.ble.Constants.SERVICE_UUID;

//import com.bignerdranch.android.bluetoothtestbed.util.ByteUtils;

public class GattServerCallback extends BluetoothGattServerCallback {

    private BluetoothGattServer mGattServer;
    private List<BluetoothDevice> mDevices;
    private Map<String, byte[]> mClientConfigurations;
    //public int mState= BluetoothProfile.STATE_DISCONNECTED;
    public static final String BLESERVER_STATE = "bleserver_state";
    //BroadcastReceiver mBroadcastReceiver;
    String network,password;
    WifiDirectHotSpot hotspot;
    private Context mContext;
    //ContentAdvertisement ca;
    BluetoothGattCharacteristic mCharacteristic;
    //NdnOcrService service;

    public GattServerCallback(Context context, WifiDirectHotSpot hotspot){
        G.Log(TAG,"GattServerCallback new "+hotspot);
        mDevices = new ArrayList<>();
        mClientConfigurations = new HashMap<>();
        mContext = context;
        this.hotspot = hotspot;

    }

    private static final String TAG = "GattServerCallback";

    public void setServer(BluetoothGattServer gattServer)
    {
        mGattServer = gattServer;
    }

    public void setNetwork(String network,String password)
    {
        this.network = network;
        this.password = password;
        G.Log(TAG, "Set network "+network+" "+password);
        /*if(mCharacteristic!=null) {
            byte[] response = (network + ":" + password).getBytes();
            mCharacteristic.setValue(response);
            G.Log(TAG, "Sending: " + StringUtils.byteArrayInHexFormat(response) + " " + new String(response));
            //mServerActionListener.notifyCharacteristicEcho(response);
            notifyCharacteristic(response, CHARACTERISTIC_ECHO_UUID);
        }*/ /*else {
            service.disconnect();
        }*/
    }

    @Override
    public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
        super.onConnectionStateChange(device, status, newState);
        G.Log(TAG,"onConnectionStateChange " + device.getAddress()
                + "\nstatus " + status
                + "\nnewState " + newState);

       // mState = newState;

        String action = BLESERVER_STATE;

        Intent broadcast = new Intent(action)
                .putExtra("state", newState);
        mContext.sendBroadcast(broadcast);

        if (newState == BluetoothProfile.STATE_CONNECTED) {
            //mServerActionListener.addDevice(device);
            mDevices.add(device);
            G.Log(TAG,"New device connected "+mDevices.size());
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
           // mServerActionListener.removeDevice(device);
            mDevices.remove(device);
            G.Log(TAG,"Device disconnected "+mDevices.size());
            mClientConfigurations.remove(device.getAddress());
            mCharacteristic=null;
        }
    }

    // The Gatt will reject Characteristic Read requests that do not have the permission set,
    // so there is no need to check inside the callback
    @Override
    public void onCharacteristicReadRequest(BluetoothDevice device,
                                            int requestId,
                                            int offset,
                                            BluetoothGattCharacteristic characteristic) {
        super.onCharacteristicReadRequest(device, requestId, offset, characteristic);

        G.Log(TAG,"onCharacteristicReadRequest " + characteristic.getUuid().toString());

        if (BluetoothUtils.requiresResponse(characteristic)) {
            // Unknown read characteristic requiring response, send failure
            //mServerActionListener.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null);
            mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null);
        }
        // Not one of our characteristics or has NO_RESPONSE property set
    }

    // The Gatt will reject Characteristic Write requests that do not have the permission set,
    // so there is no need to check inside the callback
    @Override
    public void onCharacteristicWriteRequest(BluetoothDevice device,
                                             int requestId,
                                             BluetoothGattCharacteristic characteristic,
                                             boolean preparedWrite,
                                             boolean responseNeeded,
                                             int offset,
                                             byte[] value) {
        super.onCharacteristicWriteRequest(device,
                requestId,
                characteristic,
                preparedWrite,
                responseNeeded,
                offset,
                value);
        //G.Log(TAG,"onCharacteristicWriteRequest" + characteristic.getUuid().toString()
        //        + "\nReceived: " + StringUtils.byteArrayInHexFormat(value));
        //G.Log(TAG,"onCharacteristicWriteRequest" + characteristic.getUuid().toString());
//        G.Log(TAG,"Filter "+ca.getFilter());
       // G.Log(TAG,"New Filter "+new String(value));
        if (CHARACTERISTIC_ECHO_UUID.equals(characteristic.getUuid())) {
            mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null);
            //if(ca.connectFilter(value)&&network!=null) {
            if(network!=null){
                G.Log(TAG,"Connecting");
                mContext.sendBroadcast(new Intent(WifiDirectHotSpot.DISCOVERED));
                //mServerActionListener.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null);
                // mGattServer.close();
                // Reverse message to differentiate original message & response
                //byte[] response = ByteUtils.reverse(value);
                mCharacteristic = characteristic;
               // hotspot.Start();
                byte[] response = (network+":"+password).getBytes();
                characteristic.setValue(response);
                //G.Log(TAG, "Sending: " + StringUtils.byteArrayInHexFormat(response)+" "+ new String(response));
                //mServerActionListener.notifyCharacteristicEcho(response);
                notifyCharacteristic(response, CHARACTERISTIC_ECHO_UUID);
            } else {
                G.Log("Not Connecting");
                notifyCharacteristic(new byte[]{0x00}, CHARACTERISTIC_ECHO_UUID);
            }
        }
        //mGattServer.close();
    }

    // The Gatt will reject Descriptor Read requests that do not have the permission set,
    // so there is no need to check inside the callback
    @Override
    public void onDescriptorReadRequest(BluetoothDevice device,
                                        int requestId,
                                        int offset,
                                        BluetoothGattDescriptor descriptor) {
        super.onDescriptorReadRequest(device, requestId, offset, descriptor);
        G.Log(TAG,"onDescriptorReadRequest" + descriptor.getUuid().toString());
    }

    // The Gatt will reject Descriptor Write requests that do not have the permission set,
    // so there is no need to check inside the callback
    @Override
    public void onDescriptorWriteRequest(BluetoothDevice device,
                                         int requestId,
                                         BluetoothGattDescriptor descriptor,
                                         boolean preparedWrite,
                                         boolean responseNeeded,
                                         int offset,
                                         byte[] value) {
        super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value);
        G.Log(TAG,"onDescriptorWriteRequest: " + descriptor.getUuid().toString()
                + "\nvalue: " + StringUtils.byteArrayInHexFormat(value));

        if (CLIENT_CONFIGURATION_DESCRIPTOR_UUID.equals(descriptor.getUuid())) {
           // mServerActionListener.addClientConfiguration(device, value);
            mClientConfigurations.put(device.getAddress(), value);
            mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null);
        }
    }

    @Override
    public void onNotificationSent(BluetoothDevice device, int status) {
        super.onNotificationSent(device, status);
        G.Log(TAG,"onNotificationSent");
    }


    private void notifyCharacteristicTime(byte[] value) {
        notifyCharacteristic(value, CHARACTERISTIC_TIME_UUID);
    }

    private void notifyCharacteristic(byte[] value, UUID uuid) {
        BluetoothGattService service = mGattServer.getService(SERVICE_UUID.getUuid());
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(uuid);
        G.Log(TAG,"Notifying characteristic " + characteristic.getUuid().toString()
                + ", new value: " + StringUtils.byteArrayInHexFormat(value));

        characteristic.setValue(value);
        // Indications require confirmation, notifications do not
        boolean confirm = BluetoothUtils.requiresConfirmation(characteristic);
        for (BluetoothDevice device : mDevices) {
            if (clientEnabledNotifications(device, characteristic)) {
                mGattServer.notifyCharacteristicChanged(device, characteristic, confirm);
            }
        }
    }

    private boolean clientEnabledNotifications(BluetoothDevice device, BluetoothGattCharacteristic characteristic) {
        List<BluetoothGattDescriptor> descriptorList = characteristic.getDescriptors();
        BluetoothGattDescriptor descriptor = BluetoothUtils.findClientConfigurationDescriptor(descriptorList);
        if (descriptor == null) {
            // There is no client configuration descriptor, treat as true
            return true;
        }
        String deviceAddress = device.getAddress();
        byte[] clientConfiguration = mClientConfigurations.get(deviceAddress);
        if (clientConfiguration == null) {
            // Descriptor has not been set
            return false;
        }

        byte[] notificationEnabled = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
        return clientConfiguration.length == notificationEnabled.length
                && (clientConfiguration[0] & notificationEnabled[0]) == notificationEnabled[0]
                && (clientConfiguration[1] & notificationEnabled[1]) == notificationEnabled[1];
    }

    public static IntentFilter getIntentFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BLESERVER_STATE);
        return filter;
    }
}
