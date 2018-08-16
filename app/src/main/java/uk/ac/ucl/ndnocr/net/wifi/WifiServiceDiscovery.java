package uk.ac.ucl.ndnocr.net.wifi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Handler;
import android.util.Log;

import java.util.List;

import uk.ac.ucl.ndnocr.data.NdnOcrService;
import uk.ac.ucl.ndnocr.utils.Config;
import uk.ac.ucl.ndnocr.utils.G;
import uk.ac.ucl.ndnocr.utils.TimersPreferences;


/**
 * Created by srenevic on 03/08/17.
 */
public class WifiServiceDiscovery {


    Context context;

    WifiServiceDiscovery that = this;

    private BroadcastReceiver receiver;
    private IntentFilter filter;

    private WifiManager apManager;

    public static final String TAG = "WifiServiceDiscovery";

    boolean isRunning;

    private Handler mServiceBroadcastingHandler;

    TimersPreferences timers;

    NdnOcrService service;


    enum ServiceState{
        NONE,
        DiscoverPeer,
        DiscoverService
    }
    ServiceState myServiceState = ServiceState.NONE;

    public WifiServiceDiscovery(Context context, TimersPreferences timers) {
        this.context = context;
        service = (NdnOcrService) context;
        this.timers = timers;
        apManager = (WifiManager) this.context.getSystemService(Context.WIFI_SERVICE);
        mServiceBroadcastingHandler = new Handler();

    }

    public void start() {

        if (!isRunning()) {
            isRunning = true;
            G.Log(TAG, "Service discovery start");
            if (apManager == null) {
                G.Log(TAG, "This device does not support Wi-Fi");
            } else {

                //channel = p2p.initialize(this.context, this.context.getMainLooper(), this);

                receiver = new WiFiBroadcastReceiver();
                filter = new IntentFilter();

                filter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
                filter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
                filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
                this.context.registerReceiver(receiver, filter);

                apManager.startScan();
                mServiceBroadcastingHandler.postDelayed(mServiceBroadcastingRunnable, timers.getWifiScanTime());
            }
        } else {
            G.Log(TAG,"Service already running");
        }
    }


    public void stop() {
        G.Log(TAG,"Stop");
        if(isRunning){
            //stats.setDiscoveryStatus("Off");
            this.context.unregisterReceiver(receiver);
            mServiceBroadcastingHandler.removeCallbacks(mServiceBroadcastingRunnable);
            isRunning = false;
        }

    }


    public boolean isRunning(){
        return isRunning;
    }


    private class WiFiBroadcastReceiver extends BroadcastReceiver {

        boolean connected;
        Context context;
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onreceive "+intent.getAction());

            String action = intent.getAction();
            this.context = context;
            if (WifiManager.SUPPLICANT_STATE_CHANGED_ACTION.equals(action)) {
                SupplicantState state = intent.getParcelableExtra(WifiManager.EXTRA_NEW_STATE);
                Log.d(TAG, "Supplicant state changed " + state);
                if (SupplicantState.isValidState(state)
                        && state == SupplicantState.COMPLETED) {
                    WifiInfo wifiInfo = apManager.getConnectionInfo();
                    Log.d(TAG, "Supplicant state changed " + wifiInfo.getSSID());
                    connected = true;

                    Log.d(TAG, "Connected");
                } else if (SupplicantState.isValidState(state)
                        && state == SupplicantState.DISCONNECTED) {
                    connected = false;

                        Log.d(TAG, "Disconnected");

                }
            }
            if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(action)) {

                List<ScanResult> l = apManager.getScanResults();

                for (ScanResult r : l) {

                    if (r.SSID.equals(Config.SSID) && !isAlreadyConnected()) {
                        service.linkNetworkDiscovered(Config.SSID+":"+Config.passwd);
                    }
                }
            }
        }

        private boolean isConnectedViaWifi() {
            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo mWifi = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            return mWifi.isConnected();
        }

        private boolean isAlreadyConnected(){
            return connected&&apManager.getConnectionInfo().getSSID().equals(Config.SSID);
        }
    }

    private Runnable mServiceBroadcastingRunnable = new Runnable() {
        @Override
        public void run() {
            while(apManager.startScan()==false){
                apManager.startScan();
            }

            mServiceBroadcastingHandler
                    .postDelayed(mServiceBroadcastingRunnable, timers.getWifiScanTime());
        }
    };


}
