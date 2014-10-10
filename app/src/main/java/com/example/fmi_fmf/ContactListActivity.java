package com.example.fmi_fmf;

import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

public class ContactListActivity extends FragmentActivity
        implements PositionRequestDialogFragment.RequestDialogListener {

    private static final String LOG_TAG = ContactListActivity.class.getSimpleName();
    public static final boolean D = true;

    public static final String ACTION_SHOW_REQUEST_DIALOG = "show request dialog";
    public static final String ACTION_SHOW_DECLINE_DIALOG = "show decline dialog";
    public static final String ACTION_SHOW_DISCONNECTED_DIALOG = "show disconnected dialog";
    public static final String ACTION_OPEN_MAP = "open map";

//    public static final String EXTRA_FULL_NAME = "full name";
//    public static final String EXTRA_PHONE_NUMBER = "phone number";
//
    public static boolean isActive = false;
//    private PositionRequestDialogFragment mRequestDialog;
    private NoProviderDialogFragment mNoProviderDialog;
//    private AlertDialog mNotConnectedAlert;

    private ListView mContactListView;

//    private ContactListAdapter mContactsAdapter;
//    ArrayList<FMFListEntry> mContacts;

    private String mRequesterJabberId;
//    private int mNotificationTriggered = 0;

    // Progress Dialog
//    private ProgressDialog pDialog;

    // Creating JSON Parser object
//    JSONParser jParser = new JSONParser();

//    ArrayList<HashMap<String, String>> registeredList;

    private BroadcastReceiver br = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(D) Log.d(LOG_TAG, "broadcast received");

            if(intent.getAction().equals(ACTION_SHOW_REQUEST_DIALOG)) {
                mRequesterJabberId = intent.getStringExtra(FMFCommunicationService.EXTRA_JABBER_ID);
                String from = intent.getStringExtra(FMFCommunicationService.EXTRA_FULL_NAME);
                PositionRequestDialogFragment.getInstance().setFullName(from);
                if(!PositionRequestDialogFragment.getInstance().isVisible())
                    PositionRequestDialogFragment.getInstance()
                            .show(getSupportFragmentManager(), "Position request");
            } else if(intent.getAction().equals(ACTION_SHOW_DECLINE_DIALOG)) {
                String from = intent.getStringExtra(FMFCommunicationService.EXTRA_FULL_NAME);
                AlertDialog requestDeclinedAlert = new AlertDialog.Builder(ContactListActivity.this)
                        .setTitle(R.string.title_request_declined)
                        .setMessage(from + " hat deine Anfrage abgelehnt")
                        .create();
                requestDeclinedAlert.show();
            } else if(intent.getAction().equals(ACTION_SHOW_DISCONNECTED_DIALOG)) {
                NotConnectedDialogFragment ncdf = new NotConnectedDialogFragment();
                ncdf.show(getSupportFragmentManager(),"not connected");
            }
        }
    };


    private FMFCommunicationService mService;
    private boolean mBound;
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            FMFCommunicationService.LocalBinder binder = (FMFCommunicationService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;
            if(!mService.isProviderAvailable())
            {
                if(mNoProviderDialog == null) mNoProviderDialog = new NoProviderDialogFragment();
                if(!mNoProviderDialog.isAdded()) mNoProviderDialog.show(getSupportFragmentManager(), "No Provider Dialog");
            }
            if(!mService.isConnected()) {
                NotConnectedDialogFragment ncdf = new NotConnectedDialogFragment();
                ncdf.show(getSupportFragmentManager(),"not connected");
            }
//            FMFCommunicationService.N_INFO notificationInfo = mService.getNotificationInfo();
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            boolean notificationHandled = false;
            if(getIntent() != null)
            {
                if(getIntent().getAction() != null) {
                    if(getIntent().getAction().equals(ACTION_SHOW_REQUEST_DIALOG))
                    {
    //                    mNotificationTriggered = 1337;
                        handleNotification(1337);
                        notificationHandled = true;
                    } else if(getIntent().getAction().equals(ACTION_OPEN_MAP)) {
    //                    mNotificationTriggered = 1338;
                        handleNotification(1338);
                        notificationHandled = true;
                    }
                }
            }
            if(!notificationHandled) {
                if (mService.notificationExists(1337)) {
                    //Request Notification exists
                    nm.cancel(1337);
                    mService.cancelNotification(1337);
                    handleNotification(1337);
                } else if (mService.notificationExists(1338)) {
                    //Accept Notification exists
                    nm.cancel(1338);
                    mService.cancelNotification(1338);
                    nm.cancel(1337);
                    mService.cancelNotification(1337);
                    handleNotification(1338);
                } else if(mService.notificationExists(1339)) {
                    nm.cancel(1339);
                    mService.cancelNotification(1339);
                    String from = mService.getFullName(1339);
                    AlertDialog requestDeclinedAlert = new AlertDialog.Builder(ContactListActivity.this)
                            .setTitle(R.string.title_request_declined)
                            .setMessage(from + " hat deine Anfrage abgelehnt")
                            .create();
                    requestDeclinedAlert.show();
                }
            }
            mService.updateAcceptNotificationIfExists(true);

            if(mContactListView == null) {
                mContactListView = (ListView) findViewById(R.id.contact_list);
                mContactListView.setAdapter(mService.getContactsAdapter());
                mContactListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                        onContactClicked(mService.getContactsAdapter().getItem(position).jabberID);
                    }
                });
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_contact_list);
        startService(new Intent(this,FMFCommunicationService.class));


//        Button testButton = (Button) findViewById(R.id.button);
//        testButton.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                isActive = false;
//                mService.notifyAboutAccept("Dieter Nuhr");
//                mService.notifyAboutRequest("+461761234567890@jabber.de","Bülent Ceylan");
//                mService.notifyAboutDecline("Kunibert Schlömpel");
//                isActive = true;
//            }
//        });

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.contactlistmenu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        switch (item.getItemId()) {
            case R.id.action_new:
                try
                { Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType("text/plain");
                    String sAux = "Follow My Friend ist eine tolle App, mit der Du meine Position herausfindest und mir sofort folgen kannst. Lade Dir gleich die App aus dem Store und probier's aus:\n";
                    sAux = sAux + "https://play.google.com/store/apps/details?id=bla.bla \n";
                    i.putExtra(Intent.EXTRA_TEXT, sAux);
                    startActivity(Intent.createChooser(i, "Freund einladen..."));
                }
                catch(Exception e)
                { //e.toString();
                    Toast.makeText(getBaseContext(),"Ups, das hat leider nicht funktioniert. Probiere es später erneut.",Toast.LENGTH_SHORT).show();
                }

                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (D) Log.d(LOG_TAG, "onStart");

        isActive = true;

        Intent intent = new Intent(this, FMFCommunicationService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (D) Log.d(LOG_TAG, "onStop");
        isActive = false;

        if(mBound) mService.updateAcceptNotificationIfExists(false);

//        Unbind from the service
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }

//        if(mNoProviderDialog != null) {
//            if(mNoProviderDialog.isVisible()) mNoProviderDialog.dismiss();
//        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (D) Log.d(LOG_TAG, "onPause");
        LocalBroadcastManager.getInstance(this).unregisterReceiver(br);

    }

    @Override
    protected void onResume() {
        super.onResume();
        if (D) Log.d(LOG_TAG, "onResume");
        LocalBroadcastManager.getInstance(this).registerReceiver(br,
                new IntentFilter(ACTION_SHOW_REQUEST_DIALOG));
        LocalBroadcastManager.getInstance(this).registerReceiver(br,
                new IntentFilter(ACTION_SHOW_DECLINE_DIALOG));
        LocalBroadcastManager.getInstance(this).registerReceiver(br,
                new IntentFilter(ACTION_SHOW_DISCONNECTED_DIALOG));
    }

    @Override
    public void onDialogPositiveClick() {
        if(mBound) mService.sendAccept(mRequesterJabberId);
    }

    @Override
    public void onDialogNegativeClick() {
        if(mBound) mService.sendDecline(mRequesterJabberId);
    }

    @Override
    public void onCancel() {
        if(mBound) mService.sendDecline(mRequesterJabberId);
    }

    private void onContactClicked(String contactsJabberId){
        if(mBound){
            FMFCommunicationService.RET_CODE ret = mService.sendRequest(contactsJabberId);
            if(ret == FMFCommunicationService.RET_CODE.NO_PROVIDER)
            {
                if(mNoProviderDialog == null) mNoProviderDialog = new NoProviderDialogFragment();
                mNoProviderDialog.show(getSupportFragmentManager(), "No Provider Dialog");
            } else if(ret == FMFCommunicationService.RET_CODE.NOT_CONNECTED) {
                AlertDialog notConnectedAlert = new AlertDialog.Builder(this)
                        .setTitle(R.string.title_not_connected)
                        .setMessage(R.string.message_not_connected)
                        .create();
                notConnectedAlert.show();
            } else if(ret == FMFCommunicationService.RET_CODE.OK) {
                Toast.makeText(this,"Anfrage wurde gesendet",Toast.LENGTH_LONG).show();
            }
        }
    }

    private void handleNotification(int notificationId){
        if(notificationId == 1337){
            mRequesterJabberId = mService.getJabberId();
            PositionRequestDialogFragment.getInstance().setFullName(mService.getFullName(1337));
            if(!PositionRequestDialogFragment.getInstance().isAdded())
                PositionRequestDialogFragment.getInstance().show(getSupportFragmentManager(),"Position request");
        } else if(notificationId == 1338) {
            Intent i = new Intent(this,MapsActivity.class);
            if(mService.notificationExists(1337)){
                PositionRequestDialogFragment.getInstance().setFullName(mService.getFullName(1337));
                i.setAction(MapsActivity.ACTION_SHOW_REQUEST_DIALOG)
                        .putExtra(FMFCommunicationService.EXTRA_JABBER_ID,mService.getJabberId());
            }
            startActivity(i);
        }
    }

}
