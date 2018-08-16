package uk.ac.ucl.ndnocr.net.wifi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.PowerManager;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

//import uk.ac.ucl.ndnocr.data.StatsHandler;
import uk.ac.ucl.ndnocr.data.NdnOcrService;
import uk.ac.ucl.ndnocr.utils.Config;
import uk.ac.ucl.ndnocr.utils.G;

//import uk.ac.ucl.ndnocr.backend.FireBaseLogger;


/**
 * Created by srenevic on 03/08/17.
 */
public class WifiLink  {


    static final public int ConectionStateNONE = 0;
    static final public int ConectionStatePreConnecting = 1;
    static final public int ConectionStateConnecting = 2;
    static final public int ConectionStateConnected = 3;
    static final public int ConectionStateDisconnected = 4;

    private int  mConectionState = ConectionStateNONE;

    public static final String TAG = "WifiLink";

    private boolean hadConnection = false;

    //StatsHandler stats;
    WifiManager wifiManager;
    WifiConfiguration wifiConfig;
    Context context;
    int netId = 0;

    WiFiConnectionReceiver receiver;
    private IntentFilter filter;
    boolean connected=false;
    String ssid;
    //WifiLink that;

    Handler handler;

    NdnOcrService service;

public WifiLink(Context context)
   {

        this.context = context;
        this.service = (NdnOcrService)context;

        filter = new IntentFilter();
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);

        //mWifiDirectServiceDiscovery.stop();
        receiver = new WiFiConnectionReceiver();

        //WIFI connection
        this.wifiManager = (WifiManager)this.context.getSystemService(this.context.WIFI_SERVICE);
        handler = new Handler();

        //that = this;

    }

    public void connect(String SSID, String password){

       // G.Log(TAG,"Connect "+connected+" "+mConectionState);
        if(!connected&&(mConectionState==ConectionStateNONE||mConectionState==ConectionStateDisconnected)) {
            //started = new Date();
            //FireBaseLogger.connectionStarted(context,started);
            G.Log(TAG, "New connection SSID:" + SSID + " Pass:" + password);

            this.wifiConfig = new WifiConfiguration();
            this.wifiConfig.SSID = String.format("\"%s\"", SSID);
            //this.wifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            this.wifiConfig.preSharedKey = String.format("\"%s\"", password);
            ssid = this.wifiManager.getConnectionInfo().getSSID();
            this.wifiConfig.priority = 10000;
            G.Log(TAG,"Connected to "+ssid);
            List<WifiConfiguration> wifis = this.wifiManager.getConfiguredNetworks();
            boolean result;
            if(wifis!=null) {
                for (WifiConfiguration wifi : wifis) {
                    result = this.wifiManager.disableNetwork(wifi.networkId);
                    G.Log(TAG,"Disable "+wifi.SSID+" "+result);
                    if(wifi.SSID.startsWith("DIRECT-"))
                        this.wifiManager.removeNetwork(wifi.networkId);
                }
            }

            this.context.registerReceiver(receiver, filter);
            this.netId = this.wifiManager.addNetwork(this.wifiConfig);
            this.wifiManager.disconnect();
            this.wifiManager.enableNetwork(this.netId, false);
            boolean success = this.wifiManager.reconnect();

            connected = true;
            hadConnection=false;

            handler.removeCallbacksAndMessages(null);
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    //Do something after 100ms
                    if (!hadConnection) {
                        disconnect();
                    }
                }
            },Config.wifiConnectionWaitingTime);
        }
    }

    public void disconnect(){
        handler.removeCallbacksAndMessages(null);
        G.Log(TAG,"Disconnect");
        if(connected){
            connected = false;
            this.context.unregisterReceiver(receiver);
            this.wifiManager.removeNetwork(this.netId);
            List<WifiConfiguration> wifis = this.wifiManager.getConfiguredNetworks();
            if(wifis!=null) {
                for (WifiConfiguration wifi : wifis) {
                    boolean attempt = false;
                    if (wifi.SSID.equals(ssid)) attempt = true;
                    boolean result = this.wifiManager.enableNetwork(wifi.networkId, attempt);
                    G.Log(TAG,"Wifi enable "+wifi.SSID + " "+result);

                }
            }
           // wakeLock.release();
            mConectionState=0;
            service.wifiLinkDisconnected();
        }

    }

    private class WiFiConnectionReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)) {
                NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                if(info != null) {

                    if (info.isConnected()) {
                        mConectionState = ConectionStateConnected;
                    }else if(info.isConnectedOrConnecting()) {
                        mConectionState = ConectionStateConnecting;
                    }else {
                        if(hadConnection){
                            mConectionState = ConectionStateDisconnected;
                        }else{
                            mConectionState = ConectionStatePreConnecting;
                        }
                    }

                    G.Log(TAG,"DetailedState: " + info.getDetailedState());

                    String conStatus = "";
                    if(mConectionState == WifiLink.ConectionStateNONE) {
                        conStatus = "NONE";
                    }else if(mConectionState == WifiLink.ConectionStatePreConnecting) {
                        conStatus = "PreConnecting";
                    }else if(mConectionState == WifiLink.ConectionStateConnecting) {
                        conStatus = "Connecting";
                    }else if(mConectionState == WifiLink.ConectionStateConnected) {
                        conStatus = "Connected";
                    }else if(mConectionState == WifiLink.ConectionStateDisconnected) {
                        conStatus = "Disconnected";
                        G.Log(TAG,"Had connection "+hadConnection);

                        if(hadConnection)disconnect();

                    }
                    G.Log(TAG, "Status " + conStatus);

                }

                WifiInfo wiffo = intent.getParcelableExtra(WifiManager.EXTRA_WIFI_INFO);

                //if(wiffo!=null)Log.d(TAG,"Wifiinfo "+wiffo.getSSID()+" "+that.wifiConfig.SSID);
                if(wiffo!=null&&mConectionState==ConectionStateConnected){

                    if(wiffo.getSSID().equals(wifiConfig.SSID)&&!hadConnection) {
                        //G.Log(TAG, "Ip address: " + wiffo.getIpAddress());
                        //G.Log(TAG, "Create face to " + inetAddress);
                        hadConnection=true;
                        G.Log(TAG, "Connected to " + wiffo);
                        service.wifiLinkConnected(wifiConfig.SSID);
                        //FireBaseLogger.connectionCompleted(context,started,new Date(),wiffo.getRssi(),wiffo.getLinkSpeed(),wiffo.getFrequency());

                    }

                }
            }
        }
    }


}
