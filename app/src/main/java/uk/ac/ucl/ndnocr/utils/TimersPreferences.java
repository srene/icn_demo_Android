package uk.ac.ucl.ndnocr.utils;

import android.content.Context;
import android.util.Log;

import net.grandcentrix.tray.AppPreferences;

public class TimersPreferences{

    private final static String WIFI="WF_Waiting_time";
    private final static String WIFI_SCAN="WF_scan_time";
    private final static String INTEREST_LIFE="INT_life_time";


    final AppPreferences appPreferences;

    public TimersPreferences(Context context){
        //Getting if is source device checkbox enabled from sharedpreferences
       appPreferences = new AppPreferences(context); // this Preference comes for free from the library
    }


    public long getWifiWaitingTime()
    {
        return appPreferences.getLong(WIFI,Config.wifiConnectionWaitingTime);

    }

    public void setWifiWaitingTime(long time)
    {
        appPreferences.put(WIFI,time);
    }

    public long getWifiScanTime()
    {
        return appPreferences.getLong(WIFI_SCAN,Config.wifiScanTime);

    }

    public void setWifiScanTime(long time)
    {
        appPreferences.put(WIFI_SCAN,time);
    }

    public long getInterestLifeTime()
    {
        return appPreferences.getLong(INTEREST_LIFE,Config.interestLifeTime);

    }

    public void setInterestLifeTime(long time)
    {
        appPreferences.put(INTEREST_LIFE,time);
    }




}
