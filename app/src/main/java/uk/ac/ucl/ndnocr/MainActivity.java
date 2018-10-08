package uk.ac.ucl.ndnocr;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.LinearLayout;

import com.intel.jndn.management.types.FaceStatus;
import com.intel.jndn.management.types.RibEntry;

import net.grandcentrix.tray.AppPreferences;

import uk.ac.ucl.ndnocr.ui.fragments.FaceListFragment;
import uk.ac.ucl.ndnocr.ui.fragments.FaceStatusFragment;
import uk.ac.ucl.ndnocr.ui.fragments.LogcatFragment;
import uk.ac.ucl.ndnocr.ui.fragments.LogcatSettingsFragment;
import uk.ac.ucl.ndnocr.ui.fragments.RecyclerViewFragment;
import uk.ac.ucl.ndnocr.ui.fragments.RouteInfoFragment;
import uk.ac.ucl.ndnocr.ui.fragments.RouteListFragment;
import uk.ac.ucl.ndnocr.ui.fragments.DrawerFragment;
import uk.ac.ucl.ndnocr.ui.fragments.SettingsFragment;
import uk.ac.ucl.ndnocr.utils.G;

import java.util.ArrayList;

import static uk.ac.ucl.ndnocr.ui.fragments.SettingsFragment.PREF_NDNOCR_SERVICE_SOURCE;


/**
 * Created by srenevic on 24/08/17.
 *
 * Main activity of the ndnocr app
 *
 */

public class MainActivity extends AppCompatActivity
    implements DrawerFragment.DrawerCallbacks,
               LogcatFragment.Callbacks,
               FaceListFragment.Callbacks,
               RouteListFragment.Callbacks
{

    //////////////////////////////////////////////////////////////////////////////

    /** Reference to drawer fragment */
    private DrawerFragment m_drawerFragment;
    public static final boolean DEBUG = true;
    private AppPreferences m_appPreferences;


    /** Title that is to be displayed in the ActionBar */
    private int m_actionBarTitleId = -1;

    /** Item code for drawer items: For use in onDrawerItemSelected() callback */
    public static final int DRAWER_ITEM_GENERAL = 1;
    public static final int DRAWER_ITEM_NFD = 2;
    public static final int DRAWER_ITEM_FACES = 3;
    public static final int DRAWER_ITEM_ROUTES = 4;
    // public static final int DRAWER_ITEM_STRATEGIES = 4;
    public static final int DRAWER_ITEM_LOGCAT = 5;
    public static final int DRAWER_ITEM_SETTINGS = 6;

    private ProgressDialog mProgressDialog;
    private static final String TAG = "MainActivity";
   // private FirebaseAuth mAuth;


    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;
    private static final int  PERMISSION_REQUEST_READ_EXTERNAL_STORAGE = 2;

    @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    m_appPreferences = new AppPreferences(this);
    boolean shouldBeSource = m_appPreferences.getBoolean(PREF_NDNOCR_SERVICE_SOURCE,false);
    if(shouldBeSource) {
        this.setTheme(R.style.BlackTheme);
    }
    setContentView(R.layout.activity_main);

    FragmentManager fragmentManager = getSupportFragmentManager();
    Toolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    if(shouldBeSource)getSupportActionBar().setTitle("NDN OCR Server");
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);

            builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
                }
            });
            builder.show();
        }
        if (this.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            //  builder.setTitle("This app needs location access");
            //  builder.setMessage("Please grant location access");
            //  builder.setPositiveButton(android.R.string.ok, null);
            builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSION_REQUEST_READ_EXTERNAL_STORAGE);
                }
            });
            builder.show();
        }
    }


    if (savedInstanceState != null) {
      m_drawerFragment = (DrawerFragment)fragmentManager.findFragmentByTag(DrawerFragment.class.toString());
    }

    if (m_drawerFragment == null) {
      ArrayList<DrawerFragment.DrawerItem> items = new ArrayList<DrawerFragment.DrawerItem>();
      items.add(new DrawerFragment.DrawerItem(R.string.drawer_item_general, 0,
                                             DRAWER_ITEM_GENERAL));
      //items.add(new DrawerFragment.DrawerItem(R.string.drawer_item_service, 0,
      //                                       DRAWER_ITEM_NFD));
      items.add(new DrawerFragment.DrawerItem(R.string.drawer_item_settings, 0,
                                             DRAWER_ITEM_SETTINGS));
      items.add(new DrawerFragment.DrawerItem(R.string.drawer_item_faces, 0,
                                              DRAWER_ITEM_FACES));
      items.add(new DrawerFragment.DrawerItem(R.string.drawer_item_routes, 0,
                                              DRAWER_ITEM_ROUTES));
      items.add(new DrawerFragment.DrawerItem(R.string.drawer_item_logcat, 0,
                                              DRAWER_ITEM_LOGCAT));
      //    items.add(new DrawerFragment.DrawerItem(R.string.drawer_item_strategies, 0,
      //                                            DRAWER_ITEM_STRATEGIES));


      m_drawerFragment = DrawerFragment.newInstance(items);

      fragmentManager
        .beginTransaction()
        .replace(R.id.navigation_drawer, m_drawerFragment, DrawerFragment.class.toString())
        .commit();

    }

  }


  @Override
  public void onStart() {
    super.onStart();
    //signInAnonymously();
    G.Log(TAG,"onstart");
    try {

        Fragment newFragment = new RecyclerViewFragment();
        FragmentManager fragmentManager = getSupportFragmentManager();
        fragmentManager.beginTransaction()
                .replace(R.id.main_fragment_container, newFragment, "")
                .commit();
    }catch (Exception e){};

  }



  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    Log.d(TAG,"onCreateOptionsMenu" + String.valueOf(m_drawerFragment.shouldHideOptionsMenu()));
    if (!m_drawerFragment.shouldHideOptionsMenu()) {
      updateActionBar();
      return super.onCreateOptionsMenu(menu);
    }
    else
      return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    return super.onOptionsItemSelected(item);
  }

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Convenience method that updates and display the current title in the Action Bar
   */
  @SuppressWarnings("deprecation")
  private void updateActionBar() {
    ActionBar actionBar = getSupportActionBar();
    actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
    actionBar.setDisplayShowTitleEnabled(true);
    if (m_actionBarTitleId != -1) {
      actionBar.setTitle(m_actionBarTitleId);
    }
  }

  /**
   * Convenience method that replaces the main fragment container with the
   * new fragment and adding the current transaction to the backstack.
   *
   * @param fragment Fragment to be displayed in the main fragment container.
   */
  private void replaceContentFragmentWithBackstack(Fragment fragment) {
    FragmentManager fragmentManager = getSupportFragmentManager();
    fragmentManager.beginTransaction()
        .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
        .replace(R.id.main_fragment_container, fragment)
        .addToBackStack(null)
        .commit();
  }

    @Override
    public void onBackPressed() {
        G.Log(TAG, "onBackPressed " + getFragmentManager().getBackStackEntryCount());
        android.app.Fragment myFragment = getFragmentManager().findFragmentByTag("camera");
        if (myFragment != null && myFragment.isVisible()) {
            // add your code here

        FragmentManager fragmentManager = getSupportFragmentManager();
        fragmentManager.beginTransaction()
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .replace(R.id.main_fragment_container, RecyclerViewFragment.newInstance())
                .addToBackStack(null)
                .commit();
        } else {
            super.onBackPressed();
        }

    }

  //////////////////////////////////////////////////////////////////////////////

  @Override
  public void
  onDrawerItemSelected(int itemCode, int itemNameId) {

    String fragmentTag = "ndnocr-" + String.valueOf(itemCode);
    FragmentManager fragmentManager = getSupportFragmentManager();

    G.Log(TAG,"fragmenttag "+fragmentTag);
    // Create fragment according to user's selection
    Fragment fragment = fragmentManager.findFragmentByTag(fragmentTag);
    if (fragment == null) {
      switch (itemCode) {
        case DRAWER_ITEM_GENERAL:
       try {

           fragment = RecyclerViewFragment.newInstance();

      } catch (Exception e) {}
          break;
        case DRAWER_ITEM_FACES:
            fragment = FaceListFragment.newInstance();
          break;
        case DRAWER_ITEM_ROUTES:
            fragment = RouteListFragment.newInstance();
          break;
        case DRAWER_ITEM_LOGCAT:
          fragment = LogcatFragment.newInstance();
          break;
        case DRAWER_ITEM_SETTINGS:
          fragment = SettingsFragment.newInstance();
          break;
        default:
          // Invalid; Nothing else needs to be done
          return;
      }
    }

    // Update ActionBar title
    m_actionBarTitleId = itemNameId;

    fragmentManager.beginTransaction()
      .replace(R.id.main_fragment_container, fragment, fragmentTag)
      .commit();
  }

  @Override
  public void onDisplayLogcatSettings() {
    replaceContentFragmentWithBackstack(LogcatSettingsFragment.newInstance());
  }

  @Override
  public void onFaceItemSelected(FaceStatus faceStatus) {
    replaceContentFragmentWithBackstack(FaceStatusFragment.newInstance(faceStatus));
  }

  @Override
  public void onRouteItemSelected(RibEntry ribEntry)
  {
    replaceContentFragmentWithBackstack(RouteInfoFragment.newInstance(ribEntry));
  }

}
