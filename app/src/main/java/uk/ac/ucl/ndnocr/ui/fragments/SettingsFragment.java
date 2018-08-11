
package uk.ac.ucl.ndnocr.ui.fragments;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import net.grandcentrix.tray.AppPreferences;

import uk.ac.ucl.ndnocr.App;
import uk.ac.ucl.ndnocr.R;
import uk.ac.ucl.ndnocr.utils.G;
import uk.ac.ucl.ndnocr.utils.TimersPreferences;

public class SettingsFragment extends Fragment {

  public static SettingsFragment newInstance() {
    // Create fragment arguments here (if necessary)
    return new SettingsFragment();
  }

  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    timers = new TimersPreferences(getContext());


  }

  @Override
  public View onCreateView(LayoutInflater inflater,
                           @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState)
  {
    @SuppressLint("InflateParams")
    View v =  inflater.inflate(R.layout.fragment_settings, null);
    wifiWaitingTime = (EditText) v.findViewById(R.id.wifi_waittime_input);

    wifiWaitingTime.addTextChangedListener(new TextChangedListener<EditText>(wifiWaitingTime) {
      @Override
      public void onTextChanged(EditText target, Editable s) {
        if(!s.toString().equals(""))timers.setWifiWaitingTime(Long.parseLong(s.toString()));
      }
    });

    interestLifeTime = (EditText) v.findViewById(R.id.interest_life);
    interestLifeTime.addTextChangedListener(new TextChangedListener<EditText>(interestLifeTime) {
      @Override
      public void onTextChanged(EditText target, Editable s) {
        if(!s.toString().equals(""))timers.setInterestLifeTime(Long.parseLong(s.toString()));
      }
    });

    wifiScanTime = (EditText) v.findViewById(R.id.wi_scan_input);
    wifiScanTime.addTextChangedListener(new TextChangedListener<EditText>(wifiScanTime) {
      @Override
      public void onTextChanged(EditText target, Editable s) {
        if(!s.toString().equals(""))timers.setWifiScanTime(Long.parseLong(s.toString()));
      }
    });
    /*isSource = (CheckBox) v.findViewById(R.id.checkbox_source);
    isSource.setOnClickListener(new View.OnClickListener() {

      @Override
      public void onClick(View v) {
          App app = (App)getActivity().getApplication();

          if (((CheckBox) v).isChecked()) {
              m_appPreferences.put(PREF_UBICDN_SERVICE_SOURCE,true);
              app.setSource(true);
              G.Log("Set source "+m_appPreferences.getBoolean(PREF_UBICDN_SERVICE_SOURCE,false));
          } else {
              app.setSource(false);
              m_appPreferences.put(PREF_UBICDN_SERVICE_SOURCE,false);
              G.Log("Set source "+m_appPreferences.getBoolean(PREF_UBICDN_SERVICE_SOURCE,false));

          }
      }
    });

    isWdActivated = (CheckBox) v.findViewById(R.id.checkbox_wd);
    isWdActivated.setOnClickListener(new View.OnClickListener() {

      @Override
      public void onClick(View v) {

        if (((CheckBox) v).isChecked()) {
          m_appPreferences.put(PREF_UBICDN_WD_DISCOVERY,true);
          timers.setWd(true);
          G.Log("Set wd "+m_appPreferences.getBoolean(PREF_UBICDN_WD_DISCOVERY,false));
        } else {
          timers.setWd(false);
          m_appPreferences.put(PREF_UBICDN_WD_DISCOVERY,false);
          G.Log("Set wd "+m_appPreferences.getBoolean(PREF_UBICDN_WD_DISCOVERY,false));

        }
      }
    });

    isBtActivated = (CheckBox) v.findViewById(R.id.checkbox_bt);
    isBtActivated.setOnClickListener(new View.OnClickListener() {

      @Override
      public void onClick(View v) {

        if (((CheckBox) v).isChecked()) {
          m_appPreferences.put(PREF_UBICDN_BT_DISCOVERY,true);
          timers.setBt(true);
          G.Log("Set bt "+m_appPreferences.getBoolean(PREF_UBICDN_BT_DISCOVERY,false));
        } else {
          timers.setBt(false);
          m_appPreferences.put(PREF_UBICDN_BT_DISCOVERY,false);
          G.Log("Set bt "+m_appPreferences.getBoolean(PREF_UBICDN_BT_DISCOVERY,false));
        }
      }
    });

    wifiPeerSuccessRetryTime = (EditText) v.findViewById(R.id.wifi_peer_input);
    wifiPeerFailedRetryTime = (EditText)v.findViewById(R.id.wifi_peer_failed_input);
    wifiSdSuccessRetryTime = (EditText)v.findViewById(R.id.wifi_sd_input);
    wifiSdFailedRetryTime = (EditText)v.findViewById(R.id.wifi_sd_failed_input);
    wifiHotspotRestartTime = (EditText)v.findViewById(R.id.wifi_hs_restart);*/
    //btScanTime = (EditText)v.findViewById(R.id.bt_scan_input);
    //btIdleFgTime = (EditText)v.findViewById(R.id.bt_advfg_input);
    //btIdleBgTime = (EditText)v.findViewById(R.id.bt_advbg_input);
    /*
    wifiPeerSuccessRetryTime.addTextChangedListener(new TextChangedListener<EditText>(wifiPeerSuccessRetryTime) {
      @Override
      public void onTextChanged(EditText target, Editable s) {
        G.Log("Settingsfragment","Timer "+s.toString());
        if(!s.toString().equals(""))timers.setPeerSuccessTime(Long.parseLong(s.toString()));
      }
    });
    wifiPeerFailedRetryTime.addTextChangedListener(new TextChangedListener<EditText>(wifiPeerFailedRetryTime) {
      @Override
      public void onTextChanged(EditText target, Editable s) {
        if(!s.toString().equals(""))timers.setPeerFailedTime(Long.parseLong(s.toString()));
      }
    });
    wifiSdSuccessRetryTime.addTextChangedListener(new TextChangedListener<EditText>(wifiSdSuccessRetryTime) {
      @Override
      public void onTextChanged(EditText target, Editable s) {
        if(!s.toString().equals(""))timers.setSdSuccessTime(Long.parseLong(s.toString()));
      }
    });
    wifiSdFailedRetryTime.addTextChangedListener(new TextChangedListener<EditText>(wifiSdFailedRetryTime) {
      @Override
      public void onTextChanged(EditText target, Editable s) {
        if(!s.toString().equals(""))timers.setSdFailedTime(Long.parseLong(s.toString()));
      }
    });
    wifiHotspotRestartTime.addTextChangedListener(new TextChangedListener<EditText>(wifiHotspotRestartTime) {
      @Override
      public void onTextChanged(EditText target, Editable s) {
        if(!s.toString().equals(""))timers.setHotspotRestartTime(Long.parseLong(s.toString()));
      }
    });
    btScanTime.addTextChangedListener(new TextChangedListener<EditText>(btScanTime) {
      @Override
      public void onTextChanged(EditText target, Editable s) {
        if(!s.toString().equals(""))timers.setBtScanTime(Long.parseLong(s.toString()));
      }
    });
    btIdleFgTime.addTextChangedListener(new TextChangedListener<EditText>(btIdleFgTime) {
      @Override
      public void onTextChanged(EditText target, Editable s) {
        if(!s.toString().equals(""))timers.setBtIdleFgTime(Long.parseLong(s.toString()));
      }
    });
    btIdleBgTime.addTextChangedListener(new TextChangedListener<EditText>(btIdleBgTime) {
      @Override
      public void onTextChanged(EditText target, Editable s) {
        if(!s.toString().equals(""))timers.setBtIdleBgTime(Long.parseLong(s.toString()));
      }
    });*/

    return v;
  }

  @Override
  public void onActivityCreated(@Nullable Bundle savedInstanceState)
  {
      G.Log("ServiceFragment::onActivityCreated()");
      super.onActivityCreated(savedInstanceState);
      m_appPreferences = new AppPreferences(getContext()); // this Preference comes for free from the library

  }

  @Override
  public void
  onResume() {
    G.Log("ServiceFragment::onResume()");
    super.onResume();
      //boolean shouldBeSource = m_sharedPreferences.getBoolean(PREF_UBICDN_SERVICE_SOURCE, false);
      //G.Log("Shared preferences "+m_sharedPreferences.getBoolean(PREF_UBICDN_SERVICE_SOURCE,false));
      //
    //boolean shouldBeSource = m_appPreferences.getBoolean(PREF_UBICDN_SERVICE_SOURCE,false);
    //boolean shouldUseWD = m_appPreferences.getBoolean(PREF_UBICDN_WD_DISCOVERY,false);
    //boolean shouldUseBt = m_appPreferences.getBoolean(PREF_UBICDN_BT_DISCOVERY,false);

    //isSource.setChecked(shouldBeSource);
    //isWdActivated.setChecked(shouldUseWD);
    //isBtActivated.setChecked(shouldUseBt);

    /*
    wifiPeerSuccessRetryTime.setText(Long.toString(timers.getPeerSuccessTime()), TextView.BufferType.EDITABLE);
    wifiPeerFailedRetryTime.setText(Long.toString(timers.getPeerFailedTime()), TextView.BufferType.EDITABLE);
    wifiSdSuccessRetryTime.setText(Long.toString(timers.getSdSuccessTime()), TextView.BufferType.EDITABLE);
    wifiSdFailedRetryTime.setText(Long.toString(timers.getSdFailedTime()), TextView.BufferType.EDITABLE);
    wifiHotspotRestartTime.setText(Long.toString(timers.getHotspotRestartTime()), TextView.BufferType.EDITABLE);
    btScanTime.setText(Long.toString(timers.getBtScanTime()), TextView.BufferType.EDITABLE);
    btIdleFgTime.setText(Long.toString(timers.getBtIdleFgTime()), TextView.BufferType.EDITABLE);
    btIdleBgTime.setText(Long.toString(timers.getBtIdleBgTime()), TextView.BufferType.EDITABLE);*/
    wifiWaitingTime.setText(Long.toString(timers.getWifiWaitingTime()), TextView.BufferType.EDITABLE);
    interestLifeTime.setText(Long.toString(timers.getInterestLifeTime()), TextView.BufferType.EDITABLE);
    wifiScanTime.setText(Long.toString(timers.getWifiScanTime()), TextView.BufferType.EDITABLE);
  }

  @Override
  public void
  onPause() {
    super.onPause();
    G.Log("ServiceFragment::onPause()");

  }


  //////////////////////////////////////////////////////////////////////////////

  //private CheckBox isSource;
  /*private CheckBox isWdActivated;
  private CheckBox isBtActivated;

  private EditText wifiPeerSuccessRetryTime;
  private EditText wifiPeerFailedRetryTime;
  private EditText wifiSdSuccessRetryTime;
  private EditText wifiSdFailedRetryTime;
  private EditText wifiHotspotRestartTime;
  private EditText btScanTime;
  private EditText btIdleFgTime;
  private EditText btIdleBgTime;*/

  private EditText wifiWaitingTime;
  private EditText wifiScanTime;
  private EditText interestLifeTime;

  private AppPreferences m_appPreferences;

  public static final String PREF_UBICDN_SERVICE_SOURCE = "UBICDN_SERVICE_TYPE";
  //public static final String PREF_UBICDN_WD_DISCOVERY = "UBICDN_WD_DISCOVERY";
  //public static final String PREF_UBICDN_BT_DISCOVERY = "UBICDN_BT_DISCOVERY";

  TimersPreferences timers;

  public abstract class TextChangedListener<T> implements TextWatcher {
    private T target;

    public TextChangedListener(T target) {
      this.target = target;
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {}

    @Override
    public void afterTextChanged(Editable s) {
      this.onTextChanged(target, s);
    }

    public abstract void onTextChanged(T target, Editable s);
  }
}
