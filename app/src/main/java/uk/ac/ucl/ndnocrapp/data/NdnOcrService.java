package uk.ac.ucl.ndnocrapp.data;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
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


import uk.ac.ucl.ndnocrapp.net.Discovery;
import uk.ac.ucl.ndnocrapp.net.Link;
import uk.ac.ucl.ndnocrapp.net.LinkListener;
import uk.ac.ucl.ndnocrapp.net.wifi.WifiLinkListener;
import uk.ac.ucl.ndnocrapp.utils.DispatchQueue;
import uk.ac.ucl.ndnocrapp.utils.MyNfdc;
import uk.ac.ucl.ndnocrapp.MainActivity;
import uk.ac.ucl.ndnocrapp.R;
import uk.ac.ucl.ndnocrapp.net.wifi.WifiLink;
import uk.ac.ucl.ndnocrapp.net.wifi.WifiServiceDiscovery;
import uk.ac.ucl.ndnocrapp.utils.Config;
import uk.ac.ucl.ndnocrapp.utils.G;
import uk.ac.ucl.ndnocrapp.utils.TimersPreferences;

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

import static java.lang.Thread.sleep;

public class NdnOcrService extends Service implements OnInterestCallback, OnRegisterFailed, OnData, OnTimeout, LinkListener, WifiLinkListener {

    private PowerManager mPowerManager;
    private PowerManager.WakeLock mWakeLock;
    private DatabaseHandler db;

    /** debug tag */
    public static final String TAG = "NdnOcrService";

    //Service type advertised in WiFi Direct
    //public static final String SERVICE_INSTANCE = ".ndnocr";
    //public static final String SERVICE_TYPE = ".ndnocr._tcp";

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
    public static final int SERVICE_WIFI_CONNECTED = 5;

    //Connectivity classes
    Link mWifiLink = null;
    Discovery mWifiServiceDiscovery = null;
    DispatchQueue queue;

    //While true NDN face is processing events
    public boolean shouldStop=true;
    public int retry=0;
    public int nfdRetry=0;

    /** Messenger to handle messages that are passed to the NfdService */
    protected Messenger m_serviceMessenger = null;

    /** Flag that denotes if the NFD has been started */
    private boolean m_isServiceStarted = false;
    private boolean m_isConnected = false;

    private int id;
    private int pendingInterests;
    private Handler m_handler;
    private Runnable m_statusUpdateRunnable;

    public int faceId;

    protected List<String> init = new ArrayList<>();

    boolean shouldConnect=false;

    TimersPreferences timers;


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

        m_serviceMessenger = new Messenger(new ServiceMessageHandler());
        this.queue = new DispatchQueue();
        mWifiServiceDiscovery = new WifiServiceDiscovery(this, this,timers);//, stats););
        mWifiLink = new WifiLink(this);
        m_handler = new Handler();

        m_statusUpdateRunnable = new Runnable() {
            @Override
            public void run()
            {
                new StatusUpdateTask().execute();
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

        G.Log(TAG,"KebappService::onDestroy()");
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
            faceId=0;
            HashMap<String, String> params = new HashMap<>();
            params.put("homePath", getFilesDir().getAbsolutePath());
            Set<Map.Entry<String,String>> e = params.entrySet();

            startNfd(params);
            G.Log(TAG,"onCreate timers "+timers.getWifiScanTime()+" "+timers.getInterestLifeTime()+" "+timers.getWifiWaitingTime());

            // Example how to retrieve all available NFD log modules
            List<String> modules = getNfdLogModules();
            for (String module : modules) {
                G.Log(module);
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
            mWifiLink.disconnect();
            mWifiServiceDiscovery.stop();
            m_handler.removeCallbacks(m_statusUpdateRunnable);
            G.Log(TAG, "serviceStop()");
        }
    }
    /*protected void setConnect(boolean connect){
        shouldConnect=connect;
    }*/

    private void initService()
    {

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    G.Log(TAG, "Init service thread");

                    final Face mFace = new Face("localhost");
                    KeyChain keyChain = buildTestKeyChain();
                    mFace.setCommandSigningInfo(keyChain, keyChain.getDefaultCertificateName());
                    mWifiServiceDiscovery.start(false,id);

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

    public void turnOnScreen(){
        // turn on screen
        G.Log(TAG,"ProximityActivity", "ON!");
        mPowerManager =  (PowerManager) getSystemService(Context.POWER_SERVICE);
        //PowerManager.ACQUIRE_CAUSES_WAKEUP
        mWakeLock = mPowerManager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "tag");
        mWakeLock.acquire();


    }

    @Override
    public void linkNetworkDiscovered(Link link, String network)
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
                    //turnOnScreen();
                    mWifiLink.connect(separated[0], separated[1]);

                }
            }else {
                sendToast("Link "+separated[0]+" discovered but no images to process");
            }
        }


    }

    @Override
    public void wifiLinkConnected(Link link, String network) {

        G.Log(TAG, "Wifi Link connected "+network+" "+Config.SSID);
        if(db.getPendingCount()>0){
            pendingInterests=0;
            createFaceandSend(Config.GW_IP, Config.prefix);
        }
        if(!m_isConnected) {
            m_isConnected = true;
        }
    }

    @Override
    public void wifiLinkDisconnected(Link link)
    {
        G.Log(TAG,"wifiLinkDisconnected");
        m_isConnected = false;

        if(m_isServiceStarted) {
            mWifiServiceDiscovery.start(false, id);
        }

    } // btLinkDisconnected()

    public void disconnect(){

        G.Log(TAG,"Just disconnect");
        serviceStop();
        serviceStart();
    }

    @Override
    public void linkConnected(Link link)
    {
        G.Log(TAG,"btLinkConnected");

    }

    @Override
    public void linkDisconnected(Link link)
    {
        G.Log(TAG,"btLinkDisconnected");
    }

    public void onInterest(Name name, Interest interest, Face face, long l, InterestFilter filter) {

        G.Log(TAG,"Interest received "+interest.getName().toString());
        //filter.
        // /todo check if the file exists first
        // if(interest.getNonce().equals(nonce))return;
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

                G.Log(TAG,"Get file " + data.getContent().size());
                //FireBaseLogger.videoSent(this,new Date(),interest.getName().get(2).toEscapedString());
                face.putData(data);

            } catch (IOException e) {
                G.Log(TAG, e.getMessage());
            //}catch(EncodingException e){
            //     G.Log(TAG,e.getMessage());
            }finally {
                fis.close();
            }

        } catch (FileNotFoundException e) {
            G.Log(TAG, e.getMessage());
        } catch (IOException e){
            G.Log(TAG,e.getMessage());
        }

    }

    public void onData(Interest interest, Data data){

        G.Log(TAG,"OCR message received  "+data.getContent().toString());
        try {
            boolean fg = new ForegroundCheckTask().execute(this).get();
            if(fg)        sendToast("OCR message received " + data.getContent().toString());
            else sendNotification("OCR message received " + data.getContent().toString());
        }catch (ExecutionException e){
            e.printStackTrace();
        }catch (InterruptedException e){
            e.printStackTrace();
        }
        db.setContentText(data.getName().get(3).toEscapedString(),data.getContent().toString());
        db.setContentDownloaded(Config.prefix+data.getName().get(3).toEscapedString());
        pendingInterests--;
        Intent broadcast = new Intent(NEW_RESULT);
        sendBroadcast(broadcast);
        if(pendingInterests==0)disconnect();

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
    public void createFaceandSend(final String IP, final String uri) {

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    sleep(Config.createFaceWaitingTime);
                    MyNfdc nfdc = new MyNfdc();
                    retry++;
                    G.Log(TAG,"Create face to "+IP+" for "+uri +" "+retry);
                    faceId = nfdc.faceCreate("tcp://"+IP);

                    G.Log(TAG,"Register prefix "+uri);
                    nfdc.ribRegisterPrefix(new Name(Config.prefix), faceId, 0, true, false);
                    nfdc.shutdown();

                    KeyChain keyChain = buildTestKeyChain();
                    Face mFace = new Face("localhost");
                    mFace.setCommandSigningInfo(keyChain, keyChain.getDefaultCertificateName());

                    mFace.registerPrefix(new Name("/"+getLocalIPAddress()+"/result"), that, that);
                    for(String cont : db.getPendingContent()) {
                        G.Log(TAG,"send interest "+Config.prefix+cont);

                        final Name requestName = new Name(Config.prefix+getLocalIPAddress()+"/"+cont);
                        final Name localName = new Name("/pic/"+getLocalIPAddress()+"/"+cont);
                        mFace.registerPrefix(new Name(localName), that, that);
                        Interest interest = new Interest(requestName);
                        sleep(Config.createFaceWaitingTime);
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
                    if(retry< Config.maxRetry && m_isServiceStarted) {
                        createFaceandSend(IP, uri);
                    }else {
                        //mWifiLink.disconnect();
                        retry=0;
                        //serviceStopUbiCDN();
                        //serviceStartUbiCDN();
                    }
                    G.Log(TAG, "Error " + e);
                }

            }
        }).start();
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


    private String getOwnerAddress() {
        return "192.168.49.1";
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

    /**
     * Message handler for the the ubiCDN Service.
     */
    private class ServiceMessageHandler extends Handler {

        @Override
        public void handleMessage(Message message) {
            switch (message.what) {
                case NdnOcrService.START_SERVICE:
                    G.Log(TAG,"Non source start service");
                    //source=false;
                    serviceStart();
                    replyToClient(message, NdnOcrService.SERVICE_RUNNING);
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

                case NdnOcrService.SERVICE_WIFI_CONNECTED:
                    G.Log(TAG,"Wifi connection completed");
                    // createFaceandSend();
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
}
