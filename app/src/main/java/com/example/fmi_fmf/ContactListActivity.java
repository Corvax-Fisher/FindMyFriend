package com.example.fmi_fmf;

import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.ContactsContract;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;

import org.apache.http.NameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


public class ContactListActivity extends FragmentActivity
        implements PositionRequestDialogFragment.RequestDialogListener {

    private static final String LOG_TAG = ContactListActivity.class.getSimpleName();
    public static final boolean D = true;

    public static final String ACTION_CANCEL_WAIT_PROGRESS = "cancel wait progress";
    public static final String ACTION_SHOW_CONTACTS = "show contacts";
    public static final String ACTION_SHOW_REQUEST_DIALOG = "show request dialog";
    public static final String ACTION_SHOW_DECLINE_DIALOG = "show decline dialog";
    public static final String ACTION_OPEN_MAP = "open map";
    public static final String EXTRA_FULL_NAME = "full name";
    public static final String EXTRA_PHONE_NUMBER = "phone number";

    public static final String USERNAME = "username";
    public static final String PASSWORD = "password";

    public static boolean isActive = false;
    private ProgressDialog mProgressDialog;
    private PositionRequestDialogFragment mRequestDialog;
    private NoProviderDialogFragment mNoProviderDialog;
    private AlertDialog mNotConnectedAlert;

    private ListView mContactListView;
    private ContactListAdapter mContactsAdapter;
    ArrayList<FMFListEntry> mContacts;

    private String mRequesterJabberId;
//    private int mNotificationTriggered = 0;

    // Progress Dialog
    private ProgressDialog pDialog;

    // Creating JSON Parser object
    JSONParser jParser = new JSONParser();

    ArrayList<HashMap<String, String>> registeredList;

    // url to get all products list
    private static String url_all_numbers = "http://farahzeb.de/fmi/get_all_numbers.php";

    // JSON Node names
    private static final String TAG_SUCCESS = "success";
    private static final String TAG_PRODUCTS = "registeredNumbers";
    private static final String TAG_RID = "rID";
    private static final String TAG_NUMBER = "registeredNumber";

    // products JSONArray
    JSONArray contacts = null;

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
            }

            /*else if(intent.getAction().equals(FMFCommunicationService.INFO_CONNECTED)) {
                if(mBound) {
                    mJabberIdToRealName = new ArrayList<FMFListEntry>();
                    mService.fillInJabberIdAndStatus(mJabberIdToRealName);
                }
            }*/
            //TODO (Martin):when user logged in set mLoggedIn = true, check roster presences
            //TODO          and add a roster listener
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
//            FMFCommunicationService.N_INFO notificationInfo = mService.getNotificationInfo();
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            boolean notificationHandled = false;
            if(getIntent() != null)
            {
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

//            if(mContactListView == null) {
//                mContactListView = (ListView) findViewById(R.id.ContactListView);
//                mContactListView.setAdapter(mService.getContactsAdapter());
//                mContactListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
//                    @Override
//                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
//                        onContactClicked(mService.getContactsAdapter().getItem(position).jabberID);
//                    }
//                });
//            }
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
        //this.startService(new Intent(this,FMFCommunicationService.class));

        /* TODO (Farah):
         * - Implement a custom ListAdapter with attributes like contactName, status(, etc.?)
         * - Initialize the ListView and ListAdapter
         * - Add the ListAdapter to the ListView
         * - Implement an onItemClickListener and add it to the ListView
         */

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

    //@Farah: call this method, when the user clicked on a contact in the contact list view.
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

        Intent intent = new Intent(this, FMFCommunicationService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        if(getIntent() != null) {
            if(getIntent().getAction() != null) {
                if(getIntent().getAction().equals(ACTION_SHOW_CONTACTS))
                    getAllContactNumbers();
            }
        }

        //TODO (Martin): when mLoggedIn = true, check for roster updates and add rosterlistener
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

    public void getAllContactNumbers(){
        // Hashmap for ListView
        registeredList = new ArrayList<HashMap<String, String>>();

        //Loading contacts in Background Thread
        new LoadAllContacts().execute();

        // Get listview
        ListView lv = (ListView)findViewById(R.id.contact_list);
    }

    /**
     * Background Async Task to Load all product by making HTTP Request
     * */
    class LoadAllContacts extends AsyncTask<String, String, String> {

        /**
         * Before starting background thread Show Progress Dialog
         * */
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            pDialog = new ProgressDialog(ContactListActivity.this);
            pDialog.setMessage("Kontakte werden geladen. Bitte warten...");
            pDialog.setIndeterminate(false);
            pDialog.setCancelable(false);
            pDialog.show();
        }

        /**
         * getting All products from url
         * */
        protected String doInBackground(String... args) {
            // Building Parameters
            List<NameValuePair> params = new ArrayList<NameValuePair>();
            // getting JSON string from URL
            JSONObject json = jParser.makeHttpRequest(url_all_numbers, "GET", params);

            try {
                // Checking for SUCCESS TAG
                int success = json.getInt(TAG_SUCCESS);

                if (success == 1) {
                    // products found
                    // Getting Array of Products
                    contacts = json.getJSONArray(TAG_PRODUCTS);

                    //ownNumber
                    SharedPreferences sharedPref = getSharedPreferences("FMFNumbers",Context.MODE_PRIVATE);
                    String myNumber = sharedPref.getString(EXTRA_PHONE_NUMBER,"");

                    // looping through All Products
                    for (int i = 0; i < contacts.length(); i++) {
                        JSONObject c = contacts.getJSONObject(i);

                        // Storing each json item in variable
                        String id = c.getString(TAG_RID);
                        String number = c.getString(TAG_NUMBER);

                        //getting all registered numbers and searching for their contactname in phone
                        // own number shouldnt be in the contact list activity

                        if (!number.equals(myNumber)) {
                            String contactname = getContactName(getApplicationContext(), number);

                            // creating new HashMap
                            HashMap<String, String> map = new HashMap<String, String>();

                            // adding each child node to HashMap key => value
                            map.put(TAG_RID, id);
                            map.put(TAG_NUMBER, contactname);

                            // adding HashList to ArrayList
                            if(contactname != null)
                            registeredList.add(map);
                        }

                    }
                } else {
                    // no contacts found
                    Log.d("no contacts","no contacts found");
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }

            return null;
        }

        /**
         * After completing background task Dismiss the progress dialog
         * **/
        protected void onPostExecute(String file_url) {
            // dismiss the dialog after getting all products
            pDialog.dismiss();
            // updating UI from Background Thread
            runOnUiThread(new Runnable() {

                public void run() {
                    /**
                     * Updating parsed JSON data into ListView
                     * */
                    ListAdapter adapter = new SimpleAdapter(
                            ContactListActivity.this, registeredList,
                            R.layout.single_contact, new String[]{TAG_RID,
                            TAG_NUMBER},
                            new int[]{R.id.rid, R.id.contact_number});
                    // updating listview
                    ListView lv = (ListView) findViewById(R.id.contact_list);
                    lv.setAdapter(adapter);
                }
            });

        }

    }

    public static String getContactName(Context context, String phoneNumber) {
        ContentResolver cr = context.getContentResolver();
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber));
        Cursor cursor = cr.query(uri, new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null);
        if (cursor == null) {
            return null;
        }
        String contactName = null;
        if(cursor.moveToFirst()) {
            contactName = cursor.getString(cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME));
        }

        if(cursor != null && !cursor.isClosed()) {
            cursor.close();
        }

        return contactName;
    }

}
