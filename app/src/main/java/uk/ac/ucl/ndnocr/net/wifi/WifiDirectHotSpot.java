package uk.ac.ucl.ndnocr.net.wifi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.ChannelListener;
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener;
import android.net.wifi.p2p.WifiP2pManager.GroupInfoListener;
import android.os.Handler;

import java.util.Collection;

import uk.ac.ucl.ndnocr.data.NdnOcrService;
import uk.ac.ucl.ndnocr.utils.G;
import uk.ac.ucl.ndnocr.utils.TimersPreferences;

import static android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION;
import static android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION;


/**
 * Created by srenevic on 03/08/17.
 */
public class WifiDirectHotSpot implements ConnectionInfoListener,GroupInfoListener,ChannelListener{

    WifiDirectHotSpot that = this;
    Context context;

    public static final String NETWORK_READY = "network_ready";
    public static final String DISCOVERED = "discovered";

    private WifiP2pManager p2p;
    private Channel channel;

    String mNetworkName = "";
    String mPassphrase = "";
    String mInetAddress = "";

    private BroadcastReceiver receiver;
    private IntentFilter filter;

    //private StatsHandler stats;

    public static final String TAG = "WifiDirectHotSpot";

    boolean started;

    private boolean connected=false;

    Handler handler;

    TimersPreferences timers;

    NdnOcrService service;

    public WifiDirectHotSpot(Context Context, TimersPreferences timers)
    {
        this.context = Context;
        handler = new Handler();
        this.timers = timers;
        started = false;
        this.service = (NdnOcrService)context;
    }

    @Override
    public void onChannelDisconnected() {
        G.Log(TAG,"onChannelDisconnected");
    }


    public void Start(){
        G.Log(TAG,"Trying to start");
        if(!started) {
            G.Log(TAG,"Start");
            started=true;

            p2p = (WifiP2pManager) context.getSystemService(this.context.WIFI_P2P_SERVICE);

            if (p2p == null) {
                G.Log(TAG, "This device does not support Wi-Fi Direct");
            } else {


                channel = p2p.initialize(context, context.getMainLooper(), this);
                receiver = new AccessPointReceiver();
                filter = new IntentFilter();
                filter.addAction(WIFI_P2P_STATE_CHANGED_ACTION);
                filter.addAction(WIFI_P2P_CONNECTION_CHANGED_ACTION);
                this.context.registerReceiver(receiver, filter);

                p2p.createGroup(channel, new ActionListener() {
                    public void onSuccess() {
                        G.Log(TAG, "Creating Local Group ");
                    }

                    public void onFailure(int reason) {
                        G.Log(TAG, "Local Group failed, error code " + reason);
                    }
                });
            }

        } else {
            G.Log(TAG,"Trying to set network");
            if(mNetworkName!=null&&mPassphrase!=null)
            {
                String action = NETWORK_READY;
                G.Log(TAG,"Set network");

                Intent broadcast = new Intent(action)
                        .putExtra("name", mNetworkName)
                        .putExtra("password", mPassphrase);
                context.sendBroadcast(broadcast);
            }
        }
    }

    public void Stop() {
        if(started)
        {
            G.Log(TAG,"Stop");
            started=false;
            handler.removeCallbacksAndMessages(null);
            this.context.unregisterReceiver(receiver);
            removeGroup();
        }

    }

    public boolean isRunning()
    {
        return started;
    }

    public boolean isConnected() {return connected;}

    public void removeGroup() {
        p2p.removeGroup(channel,new ActionListener() {
            public void onSuccess() {
                G.Log(TAG,"Cleared Local Group ");
            }

            public void onFailure(int reason) {
                G.Log(TAG,"Clearing Local Group failed, error code " + reason);
            }
        });
    }

    public String getNetworkName(){
        return mNetworkName;
    }

    public String getPassphrase(){
        return mPassphrase;
    }

    @Override
    public void onGroupInfoAvailable(WifiP2pGroup group) {

        try {
            Collection<WifiP2pDevice> devlist = group.getClientList();

            int numm = 0;
            for (WifiP2pDevice peer : group.getClientList()) {
                numm++;
                G.Log(TAG,"Client " + numm + " : "  + peer.deviceName + " " + peer.deviceAddress);
            }
            if(numm>0&!connected){
                G.Log(TAG,"Client " + numm +" connect");
                connected=true;
                //service.setServer();
            }
            else if(numm==0&connected){
                G.Log(TAG,"Client " + numm +" disconnect");
                connected=false;
                service.disconnect();

            }

            if(mNetworkName.equals(group.getNetworkName()) && mPassphrase.equals(group.getPassphrase())){
                G.Log(TAG,"Already have local service for " + mNetworkName + " ," + mPassphrase);
            }else {

                mNetworkName = group.getNetworkName();
                mPassphrase = group.getPassphrase();

                String action = NETWORK_READY;

                Intent broadcast = new Intent(action)
                        .putExtra("name", mNetworkName)
                        .putExtra("password", mPassphrase);
                context.sendBroadcast(broadcast);

            }

        } catch(Exception e) {
            G.Log(TAG,"onGroupInfoAvailable, error: " + e.toString());
        }
    }


    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo info) {
        try {
            if (info.isGroupOwner) {
                mInetAddress = info.groupOwnerAddress.getHostAddress();
                G.Log(TAG, "inet address " + mInetAddress);
                p2p.requestGroupInfo(channel,this);
            } else {
                G.Log(TAG,"we are client !! group owner address is: " + info.groupOwnerAddress.getHostAddress());
            }
        } catch(Exception e) {
            G.Log(TAG,"onConnectionInfoAvailable, error: " + e.toString());
        }
    }

    private class AccessPointReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
                NetworkInfo networkInfo = (NetworkInfo) intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
                if (networkInfo.isConnected()) {
                    G.Log(TAG, "We are connected, will check info now");
                    p2p.requestConnectionInfo(channel, that);
                } else {
                    G.Log(TAG, "We are DIS-connected");
                }
            }
        }
    }

}