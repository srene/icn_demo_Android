package uk.ac.ucl.ndnocr.ui.fragments;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
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
import uk.ac.ucl.ndnocr.MainActivity;
import uk.ac.ucl.ndnocr.R;
import uk.ac.ucl.ndnocr.data.NdnOcrService;
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

    isSource = (CheckBox) v.findViewById(R.id.checkbox_source);
    isSource.setOnClickListener(new View.OnClickListener() {

      @Override
      public void onClick(View v) {
        App app = (App)getActivity().getApplication();

        if (((CheckBox) v).isChecked()) {

          m_appPreferences.put(PREF_NDNOCR_SERVICE_SOURCE,true);
          app.setSource(true);
          G.Log("Set source "+m_appPreferences.getBoolean(PREF_NDNOCR_SERVICE_SOURCE,false));
        } else {
          app.setSource(false);
          m_appPreferences.put(PREF_NDNOCR_SERVICE_SOURCE,false);
          G.Log("Set source "+m_appPreferences.getBoolean(PREF_NDNOCR_SERVICE_SOURCE,false));

        }
        Intent intent = new Intent(getActivity(), MainActivity.class);
        getActivity().finish();
        startActivity(intent);
        sendServiceMessage(NdnOcrService.RESTART_SERVICE);
      }
    });

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
    bindService();
    super.onResume();
    boolean shouldBeSource = m_appPreferences.getBoolean(PREF_NDNOCR_SERVICE_SOURCE,false);
    isSource.setChecked(shouldBeSource);
    wifiWaitingTime.setText(Long.toString(timers.getWifiWaitingTime()), TextView.BufferType.EDITABLE);
    interestLifeTime.setText(Long.toString(timers.getInterestLifeTime()), TextView.BufferType.EDITABLE);
    wifiScanTime.setText(Long.toString(timers.getWifiScanTime()), TextView.BufferType.EDITABLE);
  }

  @Override
  public void
  onPause() {
    unbindService();
    super.onPause();
    G.Log("ServiceFragment::onPause()");

  }

  /**
   * Method that binds the current activity to the NDNOCRService Service.
   */
  private void
  bindService() {
    if (!m_isServiceConnected) {
      // Bind to Service
      getActivity().bindService(new Intent(getActivity(),NdnOcrService.class),
              m_ServiceConnection, Context.BIND_AUTO_CREATE);
      G.Log("ServiceFragment::bindUbiCDNService()");
    }
  }

  /**
   * Method that unbinds the current activity from the NDNOCRService Service.
   */
  private void
  unbindService() {
    if (m_isServiceConnected) {
      // Unbind from Service
      getActivity().unbindService(m_ServiceConnection);

      m_isServiceConnected = false;

      G.Log("ServiceFragment::unbindUbiCDNService()");
    }

  }

  /**
   * Convenience method to send a message to the UbiCDN Service
   * through a Messenger.
   *
   * @param message Message from a set of predefined UbiCDN Service messages.
   */
  private void
  sendServiceMessage(int message) {
    if (m_serviceMessenger == null) {
      G.Log("UbiCDN Service not yet connected");
      return;
    }
    try {
      Message msg = Message.obtain(null, message);
      msg.replyTo = m_clientMessenger;
      m_serviceMessenger.send(msg);
    } catch (RemoteException e) {
      // If Service crashes, nothing to do here
      G.Log("UbiCDN service Disconnected: " + e);
    }
  }

  /**
   * Client ServiceConnection to NDNOCRService Service.
   */
  public final ServiceConnection m_ServiceConnection = new ServiceConnection() {
    @Override
    public void
    onServiceConnected(ComponentName className, IBinder service) {
      // Establish Messenger to the Service
      m_serviceMessenger = new Messenger(service);
      m_isServiceConnected = true; // onServiceConnected runs on the main thread

      // Check if UbiCDN  Service is running
      try {

        Message msg = Message.obtain(null,NdnOcrService.CHECK_SERVICE);
        msg.replyTo = m_clientMessenger;
        m_serviceMessenger.send(msg);
      } catch (RemoteException e) {
        // If Service crashes, nothing to do here
        G.Log("onServiceConnected(): " + e);
      }

      G.Log("m_ServiceConnection::onServiceConnected()");
    }

    @Override
    public void
    onServiceDisconnected(ComponentName componentName) {
      // In event of unexpected disconnection with the Service; Not expecting to get here.
      G.Log("m_ServiceConnection::onServiceDisconnected()");
      m_isServiceConnected = false; // onServiceDisconnected runs on the main thread
    }
  };

  private class ClientHandler extends Handler {
    @Override
    public void handleMessage(Message msg) {
      switch (msg.what) {
        case NdnOcrService.SERVICE_RUNNING:
          //setServiceRunning();
          G.Log("ClientHandler: NDNOCR is Running.");
          //m_handler.postDelayed(m_statusUpdateRunnable, 500);
          break;

        case NdnOcrService.SERVICE_STOPPED:
          //setServiceStopped();
          Intent myService = new Intent(getActivity(),NdnOcrService.class);
          getActivity().stopService(myService);
          G.Log("ClientHandler: NDNOCR is Stopped.");
          break;

        default:
          super.handleMessage(msg);
          break;
      }
    }
  }

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


  //////////////////////////////////////////////////////////////////////////////

  private CheckBox isSource;

  private EditText wifiWaitingTime;
  private EditText wifiScanTime;
  private EditText interestLifeTime;

  private AppPreferences m_appPreferences;

  /** Flag that marks that application is connected to the UbiCDN Service */
  private boolean m_isServiceConnected = false;

  /** Client Message Handler */
  private final Messenger m_clientMessenger = new Messenger(new ClientHandler());

  /** Messenger connection to UbiCDN Service */
  private Messenger m_serviceMessenger = null;

  public static final String PREF_NDNOCR_SERVICE_SOURCE = "NDNOCR_SERVICE_TYPE";
  TimersPreferences timers;


}
