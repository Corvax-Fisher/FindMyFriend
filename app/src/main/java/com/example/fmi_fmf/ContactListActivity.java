package com.example.fmi_fmf;

import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import org.jivesoftware.smack.RosterEntry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Dictionary;
import java.util.HashMap;


public class ContactListActivity extends FragmentActivity
        implements PositionRequestDialogFragment.RequestDialogListener {

    private static final String LOG_TAG = ContactListActivity.class.getSimpleName();
    public static final boolean D = true;

    public static final String ACTION_CANCEL_WAIT_PROGRESS = "cancel wait progress";
    public static final String ACTION_SHOW_REQUEST_DIALOG = "show request dialog";
    public static final String ACTION_SHOW_DECLINE_DIALOG = "show decline dialog";
    public static final String ACTION_OPEN_MAP = "open map";
    public static final String EXTRA_FULL_NAME = "full name";

    public static boolean isActive = false;
    private ProgressDialog mProgressDialog;
    private PositionRequestDialogFragment mRequestDialog;
    private NoProviderDialogFragment mNoProviderDialog;
    private AlertDialog mNotConnectedAlert;

    ArrayList<FMFListEntry> mJabberIdToRealName;

    private String mRequesterJabberId;

    private BroadcastReceiver br = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(D) Log.d(LOG_TAG, "broadcast received");
            if(intent.getAction().equals(ACTION_CANCEL_WAIT_PROGRESS)) {
                mProgressDialog.dismiss();
//                Toast.makeText(getBaseContext(), "Friend didn't answer.\nrequest timed out",
//                        Toast.LENGTH_SHORT).show();
                AlertDialog.Builder builder = new AlertDialog.Builder(ContactListActivity.this);
                builder.setMessage("Friend didn't answer.\nTry again later.");
                AlertDialog pauseDialog = builder.create();
                pauseDialog.show();
            } else if(intent.getAction().equals(ACTION_SHOW_REQUEST_DIALOG)) {
                if(mRequestDialog == null) mRequestDialog = new PositionRequestDialogFragment();
                if(!mRequestDialog.isVisible())
                {
                    mRequesterJabberId = intent.getStringExtra(EXTRA_FULL_NAME);
                    mRequestDialog.show(getSupportFragmentManager(), PositionRequestDialogFragment.class.getSimpleName());
                }
            } else if(intent.getAction().equals(FMFCommunicationService.INFO_CONNECTED)) {
                if(mBound) {
                    mJabberIdToRealName = new ArrayList<FMFListEntry>();
                    mService.fillInJabberIdAndStatus(mJabberIdToRealName);
                }
            }
            //TODO (Martin):when user logged in set mLoggedIn = true, check roster presences
            //TODO          and add a roster listener
        }
    };


    private FMFCommunicationService mService;
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            FMFCommunicationService.LocalBinder binder = (FMFCommunicationService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;
            if(!mService.isProviderAvailable())
            {
                if(mNoProviderDialog == null) mNoProviderDialog = new NoProviderDialogFragment();
                mNoProviderDialog.show(getSupportFragmentManager(), "No Provider Dialog");
            }
            FMFCommunicationService.N_INFO notificationInfo = mService.getNotificationInfo();
            if(notificationInfo == FMFCommunicationService.N_INFO.ACCEPT)
                startActivity(new Intent(ContactListActivity.this,MapsActivity.class));
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };
    private boolean mBound;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        //TODO Farah: change this later,login activity nur wenn own ph number not in db


        if(true){
            startActivity(new Intent(this, RegistrationActivity.class));
        }
        else {
            setContentView(R.layout.activity_contact_list);
        }

        startService(new Intent(this,FMFCommunicationService.class));

        /* TODO (Farah):
         * - Implement a custom ListAdapter with attributes like contactName, status(, etc.?)
         * - Initialize the ListView and ListAdapter
         * - Add the ListAdapter to the ListView
         * - Implement an onItemClickListener and add it to the ListView
         */
    }

    //@Farah: call this method, when the user clicked on a contact in the contact list view.
    private void onContactClicked(String contactsJabberId, String contactsFullName){
        if(mBound){
            FMFCommunicationService.RET_CODE ret = mService.sendRequest(contactsJabberId,contactsFullName);
            if(ret == FMFCommunicationService.RET_CODE.NO_PROVIDER)
            {
                if(mNoProviderDialog == null) mNoProviderDialog = new NoProviderDialogFragment();
                mNoProviderDialog.show(getSupportFragmentManager(), "No Provider Dialog");
            } else if(ret == FMFCommunicationService.RET_CODE.NOT_CONNECTED) {
                if(mNotConnectedAlert == null)
                {
                    mNotConnectedAlert = new AlertDialog.Builder(this)
                            .setTitle(R.string.title_not_connected)
                            .setMessage(R.string.message_not_connected)
                            .create();
                }
                mNotConnectedAlert.show();
            } else if(ret == FMFCommunicationService.RET_CODE.OK) {
                if(mProgressDialog ==null) mProgressDialog = new ProgressDialog(this);
                mProgressDialog.setMessage("Auf Antwort warten...");
                mProgressDialog.setIndeterminate(true);
                mProgressDialog.show();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.my, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (D) Log.d(LOG_TAG, "onStart");

        isActive = true;

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(1337);
        Intent senderIntent = getIntent();
        if(senderIntent != null)
        {
            if(senderIntent.getAction().equals(ACTION_SHOW_REQUEST_DIALOG))
            {
                mRequesterJabberId = senderIntent.getStringExtra(EXTRA_FULL_NAME);
                if(mRequestDialog == null) mRequestDialog = new PositionRequestDialogFragment();
                mRequestDialog.show(getSupportFragmentManager(), PositionRequestDialogFragment.class.getSimpleName());
            } else if(senderIntent.getAction().equals(ACTION_OPEN_MAP)) {
                startActivity(new Intent(this,MapsActivity.class));
            }
        }
        Intent intent = new Intent(this, FMFCommunicationService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        //TODO (Martin): when mLoggedIn = true, check for roster updates and add rosterlistener
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (D) Log.d(LOG_TAG, "onStop");
        isActive = false;

//        Unbind from the service
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
        //TODO (Martin): when mLoggedIn = true, remove rosterlistener

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
                new IntentFilter(ACTION_CANCEL_WAIT_PROGRESS));
        LocalBroadcastManager.getInstance(this).registerReceiver(br,
                new IntentFilter(ACTION_SHOW_REQUEST_DIALOG));
        //TODO (Martin): add receiver for logged in info
    }

    @Override
    public void onClick(int which) {
        switch(which)
        {
            case PositionRequestDialogFragment.ID_ACCEPT:
                if(mBound) mService.sendAccept(mRequesterJabberId,"Echter Name");
                break;
            case PositionRequestDialogFragment.ID_ACCEPT_AND_REQUEST:
                //TODO (Martin): remove this option
                if(mBound) mService.sendAcceptAndRequest(mRequesterJabberId,"Echter Name");
                break;
            case PositionRequestDialogFragment.ID_DECLINE:
                if(mBound) mService.sendDecline(mRequesterJabberId, "Echter Name");
                break;
            default:
                return;
        }
    }

    @Override
    public void onCancel() {
        if(mBound) mService.sendDecline(mRequesterJabberId,"Echter Name");
    }

    private class DataBaseLookupTask extends AsyncTask< Void, Void, Void > {

        @Override
        protected Void doInBackground(Void... params) {
            for(FMFListEntry listEntry : mJabberIdToRealName)
            {
                //TODO (Farah): lookup listEntry in database
                //TODO  if entry found set the real name of the list entry
                //listEntry.realName = ...
                //TODO may do this task for each entry one by one
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

            //TODO set the list adapter
        }
    }


}
