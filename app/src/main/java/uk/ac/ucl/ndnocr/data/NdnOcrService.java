package uk.ac.ucl.ndnocr.data;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.RemoteException;
import android.widget.Toast;

import com.intel.jndn.management.types.ForwarderStatus;


import uk.ac.ucl.ndnocr.net.ble.BLEServiceDiscovery;
import uk.ac.ucl.ndnocr.net.ble.GattServerCallback;
import uk.ac.ucl.ndnocr.net.wifi.WifiDirectHotSpot;
import uk.ac.ucl.ndnocr.utils.MyNfdc;
import uk.ac.ucl.ndnocr.MainActivity;
import uk.ac.ucl.ndnocr.R;
import uk.ac.ucl.ndnocr.net.wifi.WifiLink;
import uk.ac.ucl.ndnocr.net.wifi.WifiServiceDiscovery;
import uk.ac.ucl.ndnocr.utils.Config;
import uk.ac.ucl.ndnocr.utils.G;
import uk.ac.ucl.ndnocr.utils.TimersPreferences;

import net.grandcentrix.tray.AppPreferences;
import net.named_data.jndn.Data;
import net.named_data.jndn.Face;
import net.named_data.jndn.Interest;
import net.named_data.jndn.InterestFilter;
import net.named_data.jndn.Name;
import net.named_data.jndn.OnData;
import net.named_data.jndn.OnInterestCallback;
import net.named_data.jndn.OnRegisterFailed;
import net.named_data.jndn.OnTimeout;
import net.named_data.jndn.security.KeyChain;
import net.named_data.jndn.security.SecurityException;
import net.named_data.jndn.security.identity.IdentityManager;
import net.named_data.jndn.security.identity.MemoryIdentityStorage;
import net.named_data.jndn.security.identity.MemoryPrivateKeyStorage;
import net.named_data.jndn.util.Blob;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import static android.bluetooth.le.AdvertiseSettings.ADVERTISE_MODE_BALANCED;
import static android.bluetooth.le.AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM;
import static java.lang.Thread.sleep;
import static uk.ac.ucl.ndnocr.net.ble.Constants.CHARACTERISTIC_ECHO_UUID;
import static uk.ac.ucl.ndnocr.net.ble.Constants.SERVICE_UUID;
import static uk.ac.ucl.ndnocr.ui.fragments.SettingsFragment.PREF_NDNOCR_SERVICE_SOURCE;

public class NdnOcrService extends Service implements OnInterestCallback, OnRegisterFailed, OnData, OnTimeout {

    private DatabaseHandler db;

    /** debug tag */
    public static final String TAG = "NdnOcrService";

    public NdnOcrService that = this;

    public static final int CHECK_SERVICE = 0;
    /** Message to start Service */
    public static final int START_SERVICE = 1;
    /** Message to stop Service */
    public static final int STOP_SERVICE = 2;
    /** Message to indicate that Service is running */
    public static final int SERVICE_RUNNING = 3;
    /** Message to indicate that Service is not running */
    public static final int SERVICE_STOPPED = 4;
    /** Message to indicate that WifiDirect is connected */
    public static final int RESTART_SERVICE = 5;

    //Connectivity classes
    WifiLink mWifiLink;
    WifiServiceDiscovery mWifiServiceDiscovery;
    BLEServiceDiscovery mBLEScan;
    GattServerCallback serverCallback;
    BluetoothGattServer mBluetoothGattServer;
    BluetoothManager manager;
    BluetoothAdapter btAdapter;
    WifiDirectHotSpot hotspot;
    private BluetoothLeAdvertiser adv;
    private AdvertiseCallback advertiseCallback;
    private int txPowerLevel = ADVERTISE_TX_POWER_MEDIUM;
    private int advertiseMode = ADVERTISE_MODE_BALANCED;

    //While true NDN face is processing events
    public boolean shouldStop=true;
    public int retry=0;
    public int nfdRetry=0;

    /** Messenger to handle messages that are passed to the NfdService */
    protected Messenger m_serviceMessenger = null;

    /** Flag that denotes if the NFD has been started */
    private boolean m_isServiceStarted = false;
    private boolean m_isConnected = false;

    private int pendingInterests;

    private Handler m_handler;
    private Runnable m_statusUpdateRunnable;

    //public int faceId;

    TimersPreferences timers;

    private Handler sHandler;

    BroadcastReceiver mBroadcastReceiver;

    KeyChain keyChain;

    HashMap<String, Name> pending;

    Face mFace;

    //Loading JNI libraries used to run NFD
    static {
        System.loadLibrary("crystax");
        System.loadLibrary("gnustl_shared");
        System.loadLibrary("cryptopp_shared");
        System.loadLibrary("boost_system");
        System.loadLibrary("boost_filesystem");
        System.loadLibrary("boost_date_time");
        System.loadLibrary("boost_iostreams");
        System.loadLibrary("boost_program_options");
        System.loadLibrary("boost_chrono");
        System.loadLibrary("boost_random");
        System.loadLibrary("ndn-cxx");
        System.loadLibrary("boost_thread");
        System.loadLibrary("nfd-daemon");
        System.loadLibrary("nfd-wrapper");
    }
    /**
     * Native API for starting the NFD.
     *
     * @param params NFD parameters.  Must include 'homePath' with absolute path of the home directory
     *               for the service (ContextWrapper.getFilesDir().getAbsolutePath())
     */
    public native static void
    startNfd(Map<String, String> params);

    /**
     * Native API for stopping the NFD.
     */
    public native static void
    stopNfd();

    public native static List<String>
    getNfdLogModules();

    public native static boolean
    isNfdRunning();

    public static final String NEW_RESULT = "action_download";

    @Override
    public void onCreate(){
        super.onCreate();

        db = new DatabaseHandler(this);

        timers = new TimersPreferences(this);

        sHandler = new Handler();

        m_serviceMessenger = new Messenger(new ServiceMessageHandler());
        hotspot = new  WifiDirectHotSpot(this, timers);
        mWifiServiceDiscovery = new WifiServiceDiscovery(this,timers);
        mBLEScan = new BLEServiceDiscovery(this);
        mWifiLink = new WifiLink(this);
        m_handler = new Handler();

        manager = (BluetoothManager) this.getSystemService(BLUETOOTH_SERVICE);
        btAdapter = manager.getAdapter();

        adv = btAdapter.getBluetoothLeAdvertiser();
        advertiseCallback = createAdvertiseCallback();

        m_statusUpdateRunnable = new Runnable() {
            @Override
            public void run()
            {
                new StatusUpdateTask().execute();
            }
        };

        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                G.Log(TAG, "Broadcast received " + intent);
                switch (intent.getAction()) {
                    case WifiDirectHotSpot.NETWORK_READY:
                        String network = intent.getStringExtra("name");
                        String password = intent.getStringExtra("password");
                        serverCallback.setNetwork(network, password);

                }
            }
        };

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = new Notification.Builder(this).getNotification();
        AppPreferences appPreferences = new AppPreferences(getApplicationContext());
        startForeground(1000,notification);
        serviceStart();
        return START_STICKY;
    }
    @Override
    public void onDestroy() {

        G.Log(TAG,"NdnOcrService::onDestroy()");
        serviceStop();
        stopSelf();
        m_serviceMessenger = null;
        super.onDestroy();

    }

    /////////////////////////////////////////////////////////////////////////////

    /**
     * Thread safe way of starting the service and updating the
     * started flag.
     */
    private synchronized void
    serviceStart() {

        if (!m_isServiceStarted) {
            m_isServiceStarted = true;
            G.Log(TAG,"Started");
            HashMap<String, String> params = new HashMap<>();
            pending = new HashMap<>();

            params.put("homePath", getFilesDir().getAbsolutePath());
            Set<Map.Entry<String,String>> e = params.entrySet();

            startNfd(params);

            this.registerReceiver(mBroadcastReceiver, getIntentFilter());
            //G.Log(TAG,"onCreate timers "+timers.getWifiScanTime()+" "+timers.getInterestLifeTime()+" "+timers.getWifiWaitingTime());

            // Example how to retrieve all available NFD log modules
            List<String> modules = getNfdLogModules();
            for (String module : modules) {
                G.Log(module);
            }

            if (btAdapter == null) {
                G.Log(TAG, "Bluetooth Error", "Bluetooth not detected on device");
                return;
            } else if (!btAdapter.isEnabled()) {
                G.Log(TAG, "Bluetooth Not enabled");
                return;
            }

            if(isSourceDevice()){
                startAdvertising();
                startServer();
                hotspot.Start();

            }


            m_handler.postDelayed(m_statusUpdateRunnable, 50);



            G.Log(TAG, "serviceStart()");
        } else {
            G.Log(TAG, "serviceStart(): UbiCDN Service already running!");
        }
    }

    /**
     * Thread safe way of stopping the service and updating the
     * started flag.
     */
    private synchronized void
    serviceStop() {
        if (m_isServiceStarted) {
            m_isServiceStarted = false;
            // TODO: Save NFD and NRD in memory data structures.
            stopNfd();
;
            if(!isSourceDevice()) {
                mWifiServiceDiscovery.stop();
                mBLEScan.stop();
                mWifiLink.disconnect();
            } else {
                stopAdvertising();
                stopServer();
                hotspot.Stop();
            }
            m_handler.removeCallbacks(m_statusUpdateRunnable);
            this.unregisterReceiver(mBroadcastReceiver);
            G.Log(TAG, "serviceStop()");
        }
    }


    private boolean isSourceDevice(){

        final AppPreferences appPreferences = new AppPreferences(this); // this Preference comes for free from the library

        boolean value = appPreferences.getBoolean(PREF_NDNOCR_SERVICE_SOURCE, false);

        return value;

    }


    private void initService()
    {

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    G.Log(TAG, "Init service thread");
                    keyChain = buildTestKeyChain();
                    final Face mFace = new Face("localhost");
                    KeyChain keyChain = buildTestKeyChain();
                    mFace.setCommandSigningInfo(keyChain, keyChain.getDefaultCertificateName());
                    if(!isSourceDevice()&&db.getPendingCount()>0) {
                        mWifiServiceDiscovery.start();
                        mBLEScan.start();
                    }
                    if(isSourceDevice()){
                        mFace.registerPrefix(new Name(Config.prefix), that, that);

                    } else {

                    }
                    while (m_isServiceStarted) {
                        mFace.processEvents();
                    }


                } catch (Exception ex) {
                    G.Log(TAG, ex.toString());
                }
            }
        }).start();

    }

    private void registerPictures()
    {

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final Face mFace = new Face("localhost");
                    KeyChain keyChain = buildTestKeyChain();
                    mFace.setCommandSigningInfo(keyChain, keyChain.getDefaultCertificateName());
                    for(String name: db.getPendingContent()){
                        mFace.registerPrefix(new Name("/pic/"+getLocalIPAddress()+"/"+name),that,that);
                    }
                    while (m_isServiceStarted) {
                        mFace.processEvents();
                    }


                } catch (Exception ex) {
                    G.Log(TAG, ex.toString());
                }
            }
        }).start();

    }
    @Override
    public IBinder onBind(Intent intent) {

        G.Log(TAG,"Service onBind");
        return m_serviceMessenger.getBinder();
    }

    public void linkNetworkDiscovered(String network)
    {

        String[] separated = network.split(":");
        G.Log(TAG, "Frame received " + network+" "+db.getContentDownloaded().size()+" "+db.getPendingCount()+" "+separated.length);

        if(separated.length<=3) {
            if(db.getPendingCount()>0){
             //   setConnect(true);
                sendToast("Sending images to process to link "+separated[0]);
                if(!separated[0].equals("")&&!separated[1].equals("")) {
                    //shouldConnect=false;
                    G.Log(TAG,"StartConnection");
                    mWifiServiceDiscovery.stop();
                    mBLEScan.stop();
                    //turnOnScreen();
                    mWifiLink.connect(separated[0], separated[1]);

                }
            }else {
                sendToast("Link "+separated[0]+" discovered but no images to process");
            }
        }


    }

    public void wifiLinkConnected(String network) {

        G.Log(TAG, "Wifi Link connected "+network+" "+Config.SSID);
        if(db.getPendingCount()>0){
            pendingInterests=0;
            List<String> interests = new ArrayList<>();
            for(String pending : db.getPendingContent()){
                interests.add(Config.prefix+getLocalIPAddress()+"/"+pending);
            }
            registerPictures();
            createFaceandSend(Config.GW_IP, Config.prefix,interests);
        }
        if(!m_isConnected) {
            m_isConnected = true;
        }
    }

    public void wifiLinkDisconnected()
    {
        G.Log(TAG,"wifiLinkDisconnected");
        m_isConnected = false;

        if(m_isServiceStarted&&(db.getPendingCount()>0)) {
            mWifiServiceDiscovery.start();
            mBLEScan.start();
        }

    }  // btLinkDisconnected()

    public void disconnect(){

        G.Log(TAG,"Just disconnect");
        if(m_isServiceStarted) {
            serviceStop();
            serviceStart();
        }
    }

    public void onInterest(Name name, Interest interest, Face face, long l, InterestFilter filter) {

        G.Log(TAG,"Interest received "+interest.getName().toString() +" "+interest.getName().get(0).toEscapedString());

        if((interest.getName().getPrefix(2).toString()+"/").equals(Config.prefix)){
            String address = interest.getName().get(2).toEscapedString();
            List<String> interests = new ArrayList<>();
            interests.add("/pic/"+address+"/"+interest.getName().get(3).toEscapedString());
            createFaceandSend(address,"/pic",interests);
            pending.put(interest.getName().get(3).toEscapedString(),interest.getName());


        }else if (interest.getName().get(0).toEscapedString().equals("pic")) {
            try {
                byte[] bytes;
                Data data;

                File dir = getFilesDir();
                File[] subFiles = dir.listFiles();
                for (File file : subFiles) {
                    G.Log("Filename " + file.getAbsolutePath() + " " + file.getName() + " " + file.length());
                }
                String filename = interest.getName().get(2).toEscapedString();
                File f = new File(getFilesDir() + "/" + filename);
                InputStream fis = new FileInputStream(f);
                bytes = new byte[(int) f.length()];


                try {
                    fis.read(bytes);
                    Blob blob = new Blob(bytes);
                    data = new Data();
                    data.setName(interest.getName());
                    //data.wireDecode(blob);
                    data.setContent(blob);

                    G.Log(TAG, "Get file " + data.getContent().size());
                    face.putData(data);

                } catch (IOException e) {
                    G.Log(TAG, e.getMessage());
                } finally {
                    fis.close();
                }

            } catch (FileNotFoundException e) {
                G.Log(TAG, e.getMessage());
            } catch (IOException e) {
                G.Log(TAG, e.getMessage());
            }
        }

    }

    public void onData(Interest interest, Data data){

        G.Log(TAG, "Data received " + data.getName().toString());
        if((interest.getName().getPrefix(2)+"/").equals(Config.prefix)) {
            G.Log(TAG, "OCR message received  " + data.getContent().toString());
            try {
                boolean fg = new ForegroundCheckTask().execute(this).get();
                if (fg) sendToast("OCR message received " + data.getContent().toString());
                else sendNotification("OCR message received " + data.getContent().toString());
            } catch (ExecutionException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            db.setContentText(data.getName().get(3).toEscapedString(), data.getContent().toString());
            db.setContentDownloaded(Config.prefix + data.getName().get(3).toEscapedString());
            pendingInterests--;
            Intent broadcast = new Intent(NEW_RESULT);
            sendBroadcast(broadcast);
            if (pendingInterests == 0) disconnect();
        } else if (interest.getName().get(0).toEscapedString().equals("pic")) {
            G.Log(TAG, "Picture recevied " + data.getName().get(2).toEscapedString());
            try {
                OcrText ocr = new OcrText(this);
                Blob b = data.getContent();
                File f = new File(getFilesDir() + "/" + data.getName().get(2).toEscapedString());
                FileOutputStream fos = new FileOutputStream(f);
                fos.write(b.getImmutableArray());
                String s = ocr.inspect(Uri.fromFile(f));
                G.Log(TAG, "Text recevied " + s);
                Data d = new Data();

                d.setName(pending.get(data.getName().get(2).toEscapedString()));
                d.setContent(new Blob(s.getBytes()));
                mFace.putData(d);
            }catch (IOException e){
                e.printStackTrace();
            }
        }


    }

    public void onTimeout(Interest interest){
        G.Log(TAG,"Timeout for interest "+interest.getName());
        pendingInterests--;
        if(pendingInterests==0)disconnect();

    }

    public void onRegisterFailed(Name name){
        G.Log(TAG, "Failed to register the data");

    }

    public void sendToast(final String msg)
    {
        // prepare intent which is triggered if the
// notification is selected


        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {

            @Override
            public void run() {
                Toast.makeText(getApplicationContext(),
                        msg,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void sendNotification(final String msg){
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pIntent = PendingIntent.getActivity(this, 0, intent, 0);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

// build notification
// the addAction re-use the same intent to keep the example short
        Notification n  = new Notification.Builder(this)
                .setContentTitle("NDN OCR Application")
                .setContentText(msg)
                .setSmallIcon(R.drawable.icon)
                .setContentIntent(pIntent)
                .setAutoCancel(true)
                .addAction(R.drawable.icon, "Call", pIntent)
                .addAction(R.drawable.icon, "More", pIntent)
                .addAction(R.drawable.icon, "And more", pIntent).build();


        NotificationManager notificationManager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        notificationManager.notify(0, n);

        // Turn on the screen for notification
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        boolean result= Build.VERSION.SDK_INT>= Build.VERSION_CODES.KITKAT_WATCH&&powerManager.isInteractive()|| Build.VERSION.SDK_INT< Build.VERSION_CODES.KITKAT_WATCH&&powerManager.isScreenOn();

        if (!result){
            PowerManager.WakeLock wl = powerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK |PowerManager.ACQUIRE_CAUSES_WAKEUP |PowerManager.ON_AFTER_RELEASE,"MH24_SCREENLOCK");
            wl.acquire(10000);
            PowerManager.WakeLock wl_cpu = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"MH24_SCREENLOCK");
            wl_cpu.acquire(10000);
        }
    }

    /* Creates the face towards the group owner and send interests to the NFD daemon for
      any pending video not received once connected to the WifiDirect network (WifiLink succeed)
     */
        public void createFaceandSend(final String IP, final String uri, final List<String> interest) {

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    sleep(Config.createFaceWaitingTime);
                    MyNfdc nfdc = new MyNfdc();
                    retry++;
                    G.Log(TAG,"Create face to "+IP);
                    int faceId = nfdc.faceCreate("tcp://"+IP);
                    nfdc.ribRegisterPrefix(new Name(uri), faceId, 0, true, false);

                    //G.Log(TAG,"Register prefix "+uri);
                    nfdc.shutdown();

                    mFace = new Face("localhost");
                    mFace.setCommandSigningInfo(keyChain, keyChain.getDefaultCertificateName());

                    //mFace.registerPrefix(new Name("/"+getLocalIPAddress()+"/result"), that, that);
                    for(String inter: interest)
                    {
                        G.Log(TAG,"send interest "+inter);
                        final Name requestName = new Name(inter);
                        Interest interest = new Interest(requestName);
                        interest.setInterestLifetimeMilliseconds(timers.getInterestLifeTime());
                        mFace.expressInterest(interest, that, that);
                        pendingInterests++;

                    }

                    shouldStop=false;
                    retry=0;
                    while (!shouldStop) {
                        mFace.processEvents();
                    }
                    mFace.shutdown();
                } catch (Exception e) {
                    if(retry< Config.maxRetry) {
                        createFaceandSend(IP, uri,interest);
                    }else {
                        retry=0;
                    }
                    G.Log(TAG, "Error " + e);
                }

            }
        }).start();
    }

    public String getLocalIPAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress()) {
                        if (inetAddress instanceof Inet4Address) { // fix for Galaxy Nexus. IPv4 is easy to use :-)
                            //G.Log(TAG,"IP address "+getDottedDecimalIP(inetAddress.getAddress()));
                            if(getDottedDecimalIP(inetAddress.getAddress()).startsWith("192.168."))
                                return getDottedDecimalIP(inetAddress.getAddress());
                        }
                        //return inetAddress.getHostAddress().toString(); // Galaxy Nexus returns IPv6
                    }
                }
            }
        } catch (SocketException ex) {
            //Log.e("AndroidNetworkAddressFactory", "getLocalIPAddress()", ex);
        } catch (NullPointerException ex) {
            //Log.e("AndroidNetworkAddressFactory", "getLocalIPAddress()", ex);
        }
        return null;
    }


    private String getDottedDecimalIP(byte[] ipAddr) {
        //convert to dotted decimal notation:
        String ipAddrStr = "";
        for (int i=0; i<ipAddr.length; i++) {
            if (i > 0) {
                ipAddrStr += ".";
            }
            ipAddrStr += ipAddr[i]&0xFF;
        }
        return ipAddrStr;
    }


    private AdvertiseCallback createAdvertiseCallback() {
        return new AdvertiseCallback() {
            @Override
            public void onStartFailure(int errorCode) {
                switch (errorCode) {
                    case ADVERTISE_FAILED_DATA_TOO_LARGE:
                        G.Log(TAG,"ADVERTISE_FAILED_DATA_TOO_LARGE");
                        break;
                    case ADVERTISE_FAILED_TOO_MANY_ADVERTISERS:
                        G.Log(TAG,"ADVERTISE_FAILED_TOO_MANY_ADVERTISERS");
                        break;
                    case ADVERTISE_FAILED_ALREADY_STARTED:
                        G.Log(TAG,"ADVERTISE_FAILED_ALREADY_STARTED");
                        break;
                    case ADVERTISE_FAILED_INTERNAL_ERROR:
                        G.Log(TAG,"ADVERTISE_FAILED_INTERNAL_ERROR");
                        break;
                    case ADVERTISE_FAILED_FEATURE_UNSUPPORTED:
                        G.Log(TAG,"ADVERTISE_FAILED_FEATURE_UNSUPPORTED");
                        break;
                    default:
                        G.Log(TAG,"startAdvertising failed with unknown error " + errorCode);
                        break;
                }
            }
        };
    }


    private void startAdvertising() {
        G.Log(TAG, "Starting ADV, Tx power");

        AdvertiseSettings advertiseSettings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(advertiseMode)
                .setTxPowerLevel(txPowerLevel)
                .setConnectable(true)
                .build();

        AdvertiseData advertiseData = new AdvertiseData.Builder()
                // .addServiceData(SERVICE_UUID, serviceData)
                .addServiceUuid(SERVICE_UUID)
                .setIncludeTxPowerLevel(false)
                .setIncludeDeviceName(false)
                .build();

        adv.startAdvertising(advertiseSettings, advertiseData, advertiseCallback);
    }

    private void stopAdvertising() {
        G.Log(TAG, "Stopping ADV");
        adv.stopAdvertising(advertiseCallback);
        //setEnabledViews(true, namespace, instance, rndNamespace, rndInstance, txPower, txMode);
    }

    /**
     * Initialize the GATT server instance with the services/characteristics
     * from the Time Profile.
     */
    private void startServer() {
        G.Log(TAG, "Start server "+hotspot+" "+manager.getConnectedDevices(BluetoothProfile.GATT));

        serverCallback = new GattServerCallback(this,hotspot);
        mBluetoothGattServer = manager.openGattServer(this, serverCallback);
        serverCallback.setServer(mBluetoothGattServer);

        if (mBluetoothGattServer == null) {
            G.Log(TAG, "Unable to create GATT server");
            return;
        }
        setupServer();
    }

    private void stopServer() {
        if (mBluetoothGattServer == null) return;
        mBluetoothGattServer.clearServices();
        mBluetoothGattServer.close();
    }

    // GattServer

    private void setupServer() {
        BluetoothGattService service = new BluetoothGattService(SERVICE_UUID.getUuid(),
                BluetoothGattService.SERVICE_TYPE_PRIMARY);

        // Write characteristic
        BluetoothGattCharacteristic writeCharacteristic = new BluetoothGattCharacteristic(
                CHARACTERISTIC_ECHO_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                // Somehow this is not necessary, the client can still enable notifications
//                        | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_WRITE);

        service.addCharacteristic(writeCharacteristic);

        mBluetoothGattServer.addService(service);
    }

    public static KeyChain buildTestKeyChain() throws SecurityException {
        MemoryIdentityStorage identityStorage = new MemoryIdentityStorage();
        MemoryPrivateKeyStorage privateKeyStorage = new MemoryPrivateKeyStorage();
        IdentityManager identityManager = new IdentityManager(identityStorage, privateKeyStorage);
        KeyChain keyChain = new KeyChain(identityManager);
        try {
            keyChain.getDefaultCertificateName();
        } catch (SecurityException e) {
            keyChain.createIdentity(new Name("/test/identity"));
            keyChain.getIdentityManager().setDefaultIdentity(new Name("/test/identity"));
        }
        return keyChain;
    }

    class ForegroundCheckTask extends AsyncTask<Context, Void, Boolean> {

        @Override
        protected Boolean doInBackground(Context... params) {
            final Context context = params[0].getApplicationContext();
            return isAppOnForeground(context);
        }

        private boolean isAppOnForeground(Context context) {
            ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningAppProcessInfo> appProcesses = activityManager.getRunningAppProcesses();
            if (appProcesses == null) {
                return false;
            }
            final String packageName = context.getPackageName();
            for (ActivityManager.RunningAppProcessInfo appProcess : appProcesses) {
                if (appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND && appProcess.processName.equals(packageName)) {
                    return true;
                }
            }
            return false;
        }
    }


    /**
     * Message handler for the the ubiCDN Service.
     */
    private class ServiceMessageHandler extends Handler {

        @Override
        public void handleMessage(Message message) {
            switch (message.what) {
                case NdnOcrService.START_SERVICE:
                    G.Log(TAG,"Non source start service");
                    serviceStart();
                    replyToClient(message, NdnOcrService.SERVICE_RUNNING);
                    break;
                case NdnOcrService.RESTART_SERVICE:
                    G.Log(TAG,"Non source start service");
                    if(m_isServiceStarted) {
                        serviceStop();
                        serviceStart();
                    }
                    break;
                case NdnOcrService.CHECK_SERVICE:
                    if(m_isServiceStarted)
                        replyToClient(message, NdnOcrService.SERVICE_RUNNING);
                    else
                        replyToClient(message, NdnOcrService.SERVICE_STOPPED);
                    break;
                case NdnOcrService.STOP_SERVICE:
                    G.Log(TAG,"Non source stop service");
                    serviceStop();
                    stopForeground(true);
                    stopSelf();
                    replyToClient(message, NdnOcrService.SERVICE_STOPPED);
                    break;

                default:
                    super.handleMessage(message);
                    break;
            }
        }

        private void
        replyToClient(Message message, int replyMessage) {
            try {
                message.replyTo.send(Message.obtain(null, replyMessage));
            } catch (RemoteException e) {
                // Nothing to do here; It means that client end has been terminated.
            }
        }
    }

    private class StatusUpdateTask extends AsyncTask<Void, Void, ForwarderStatus> {
        /**
         * @param voids
         * @return ForwarderStatus if operation succeeded, null if operation failed
         */
        @Override
        protected ForwarderStatus
        doInBackground(Void... voids)
        {
            try {
                MyNfdc nfdcHelper = new MyNfdc();
                ForwarderStatus fs = nfdcHelper.generalStatus();
                nfdcHelper.shutdown();
                return fs;
            }
            catch (Exception e) {
                nfdRetry++;
                G.Log(TAG,"Error communicating with NFD (" + e.getMessage() + ")");
                if(nfdRetry> Config.nfdMaxRetry){
                    serviceStop();
                    serviceStart();
                }
                return null;
            }
        }

        @Override
        protected void
        onPostExecute(ForwarderStatus fs)
        {
            if (fs == null) {
                // when failed, try after 0.5 seconds
                m_handler.postDelayed(m_statusUpdateRunnable, 50);
            }
            else {
                // refresh after 5 seconds
                initService();
            }
        }
    }

    public static IntentFilter getIntentFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiDirectHotSpot.NETWORK_READY);
        return filter;
    }
}
