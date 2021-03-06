package com.example.fmi_fmf;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.ContactsContract;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.util.ArrayMap;
import android.util.Log;
import android.widget.Toast;

import org.apache.http.NameValuePair;
import org.jivesoftware.smack.AccountManager;
import org.jivesoftware.smack.Chat;
import org.jivesoftware.smack.ChatManager;
import org.jivesoftware.smack.ChatManagerListener;
import org.jivesoftware.smack.MessageListener;
import org.jivesoftware.smack.RosterEntry;
import org.jivesoftware.smack.RosterListener;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public class FMFCommunicationService extends Service implements LocationListener, RosterListener {

    private final String LOG_TAG = FMFCommunicationService.class.getSimpleName();

    public static final String ACTION_SEND_STOP = "send stop";

    public static final String EXTRA_FRIEND_LOCATION = "friend location";

    public static final String ACTION_REGISTER = "register";
    public static final String EXTRA_PHONE_NUMBER = "phone number";

    public static final String ACTION_CANCEL_NOTIFICATION = "cancel accept notification";
    public static final String EXTRA_NOTIFICATION_ID = "notification id";
    public static final String USERNAME = "username";
    public static final String PASSWORD = "password";

    public static final String EXTRA_JABBER_ID = "jabber id";
    public static final String EXTRA_FULL_NAME = "full name";

    public static final String ACTION_PROCESS_REQUEST_RESULT = "process request result";
    public static final String EXTRA_REQUEST_ACCEPTED = "request accepted";

    public static final String EXTRA_REGISTRATION_SUCCESSFUL = "registration successful";

    public static final String ACTION_PROCESS_DB_REGISTRATION_RESULT = "process db registration result";
    public static final String ACTION_UNREGISTER = "unregister";

    private final Integer CONNECT = 1;
    private final Integer CONNECTED = 2;
    private final Integer NOT_CONNECTED = 3;
    private final Integer LOGIN = 4;
    private final Integer LOGGED_IN = 5;
    private final Integer NOT_LOGGED_IN = 6;
    private AsyncTask<Integer, Void, Integer> mConnectionTask;
    private boolean mLoadContactsTaskIsRunning = false;

    public enum RET_CODE {OK, NO_PROVIDER, NOT_CONNECTED}

    private final String XMPP_SERVER = "einfachjabber.de";

    private LocationManager mLocationManager;
    private XMPPConnection mConnection = new XMPPTCPConnection(XMPP_SERVER);
    private ChatManager mChatManager;
    private Chat mReceiverChat;
    private Map<String,Chat> mSenderChats;
    private Map<String,Boolean> mAcceptedJabberIds;
    private String mProvider;
    private NotificationManager mNotificationManager;

//    private ArrayList<FMFListEntry> mContacts;
    private ContactListAdapter mContactsAdapter;

    private String mJabberId;
    private Map<Integer,String> mFullNameFromNotificationId;
    private NotificationCompat.Builder mAcceptedNotificationBuilder;

//    private ArrayList<FMFListEntry> mContactListEntries;
//    private Map<String,Integer> mRosterMap;
    private Set<Integer> mExistingNotifications;

    private String mUserName;
    private String mPassword;

    private NetworkInfo mActiveNetworkInfo;

    private Handler mUpdateListViewHandler = new Handler(Looper.getMainLooper());

    // Binder given to clients
    private final IBinder mBinder = new LocalBinder();

    private String mCurrentLocation;
    private boolean mIsInteractive = true;
    private String mCurrentPresenceStatus = "";

    private BroadcastReceiver br = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction().equals(LocationManager.PROVIDERS_CHANGED_ACTION))
            {
                if(ContactListActivity.D) Log.d(LOG_TAG,"Providers changed");
                // Creating a criteria object to retrieve provider
                Criteria criteria = new Criteria();
                criteria.setAccuracy(Criteria.ACCURACY_FINE);
                // Getting the name of the best provider
                String bestProvider = mLocationManager.getBestProvider(criteria, true);
                if(mProvider != null) {
                    if (!mProvider.equals(bestProvider))
                        mLocationManager.removeUpdates(FMFCommunicationService.this);
                }
                mProvider = bestProvider;
                sendCurrentPresence();
            } else if(intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                checkForActiveNetworks();
//                boolean connected = !intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY,false);
//                if(!connected)
//                    LocalBroadcastManager.getInstance(FMFCommunicationService.this)
//                        .sendBroadcast(new Intent(ContactListActivity.ACTION_SHOW_DISCONNECTED_DIALOG));
//                else if(!mIsAnyNetworkActive && connected) tryToLogIn();
//                mIsAnyNetworkActive = connected;
            } else if(intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                mIsInteractive = true;
                if(ContactListActivity.D) Log.d(LOG_TAG,"Is interactive.");
            } else if(intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                mIsInteractive = false;
                if(ContactListActivity.D) Log.d(LOG_TAG,"Is not interactive.");
            }
        }
    };

//    public FMFCommunicationService() {
//    }

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
        FMFCommunicationService getService() {
            // Return this instance of LocalService so clients can call public methods
            return FMFCommunicationService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        if(ContactListActivity.D)
            Log.d(FMFCommunicationService.class.getSimpleName(), "binding service...");

        return mBinder;
    }

    /** methods for clients */
    public boolean isProviderAvailable() {
        return ((mProvider != null) && !mProvider.equals(LocationManager.PASSIVE_PROVIDER)) ;
    }

    public RET_CODE sendRequest(String myFriendsJabberId) {
        if(mProvider == null) return RET_CODE.NO_PROVIDER;
        if(!mConnection.isAuthenticated()) return RET_CODE.NOT_CONNECTED;
        if(ContactListActivity.D) Log.d(LOG_TAG, "sending a request to " + myFriendsJabberId);
        mReceiverChat = mChatManager.createChat(myFriendsJabberId,mChatMessageListener);
        try {
            mReceiverChat.sendMessage("P");
        } catch (XMPPException e) {
            e.printStackTrace();
        } catch (SmackException.NotConnectedException e) {
            e.printStackTrace();
            return RET_CODE.NOT_CONNECTED;
        }
        return RET_CODE.OK;
    }

    public void sendAccept(String myFriendsJabberId) {
        if(ContactListActivity.D) Log.d(LOG_TAG,"sending an accept to " + myFriendsJabberId);

        if(mAcceptedJabberIds == null) mAcceptedJabberIds = new HashMap<String,Boolean>();
        mAcceptedJabberIds.put(myFriendsJabberId, false);
        Chat chat = mSenderChats.get(myFriendsJabberId);
        if(chat != null) try {
            chat.sendMessage("A");
            if(mProvider != null)
                mLocationManager.requestLocationUpdates(mProvider,5000,0,FMFCommunicationService.this);
        } catch (XMPPException e) {
            e.printStackTrace();
        } catch (SmackException.NotConnectedException e) {
            e.printStackTrace();
        }
    }

    public void sendDecline(String myFriendsJabberId) {
        if(ContactListActivity.D) Log.d(LOG_TAG, "sending decline to " + myFriendsJabberId);

        Chat chat = mSenderChats.get(myFriendsJabberId);
        if(chat != null) try {
            chat.sendMessage("N");
        } catch (XMPPException e) {
            e.printStackTrace();
        } catch (SmackException.NotConnectedException e) {
            e.printStackTrace();
        }
    }

    public ContactListAdapter getContactsAdapter() {
        return mContactsAdapter;
    }

    public String getFullName(int notificationId) { return mFullNameFromNotificationId.get(notificationId); }

    public String getJabberId() {
        return mJabberId;
    }

    public void updateAcceptNotificationIfExists(boolean contactListActivityIsActive) {
        if(mExistingNotifications.contains(1338)) {
            Intent resultIntent;
            if(contactListActivityIsActive)
                resultIntent= new Intent(this, MapsActivity.class);
            else {
                resultIntent = new Intent(this, ContactListActivity.class);
                resultIntent.setAction(ContactListActivity.ACTION_OPEN_MAP)
                        .putExtra(EXTRA_JABBER_ID, mJabberId);
            }

            PendingIntent resPI = PendingIntent.getActivity(
                    this, 1338, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            mAcceptedNotificationBuilder.setContentIntent(resPI).setDefaults(0);
            mNotificationManager.notify(1338, mAcceptedNotificationBuilder.build());
        }
    }

    public void cancelNotification(int notificationId){
        mExistingNotifications.remove(notificationId);
    }

    public boolean notificationExists(int notificationId){
        return mExistingNotifications.contains(notificationId);
    }

    public boolean isAnyNetworkActive() {
        return mActiveNetworkInfo != null;
    }

    public void sendMapPresenceUpdate() {
        try {
            if(ContactListActivity.D) Log.d(LOG_TAG,"sending map presence");
            Presence p = new Presence(Presence.Type.available,"Available:Map",100, Presence.Mode.available);
            mConnection.sendPacket(p);
            mCurrentPresenceStatus = p.getStatus();
        } catch (SmackException.NotConnectedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        if(ContactListActivity.D) Log.d(LOG_TAG,"Service gestartet");

        mConnection.addConnectionListener(new FMFConnectionListener() {
            @Override
            public void reconnectionSuccessful() {
                sendCurrentPresence();
            }
        });

//        mContacts = new ArrayList<FMFListEntry>();
        mContactsAdapter = new ContactListAdapter(
                FMFCommunicationService.this,R.layout.contact_list_item,R.id.nameView);

        SharedPreferences sharedPref = getSharedPreferences("FMFNumbers", Context.MODE_PRIVATE);
        mUserName = sharedPref.getString(USERNAME, "");
        mPassword = sharedPref.getString(PASSWORD, "");

        if(ContactListActivity.D) Log.d(LOG_TAG,"PASSWORD: " + mPassword);

        tryToLogIn();

        mExistingNotifications = new HashSet<Integer>(3);
        mFullNameFromNotificationId = new ArrayMap<Integer, String>(2);

        mLocationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        checkForActiveNetworks();

        // Creating a criteria object to retrieve provider
        Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_FINE);
        // Getting the name of the best provider
        mProvider = mLocationManager.getBestProvider(criteria, true);

        // Getting Current Location From GPS
//        if(mProvider != null) {
//            Location location = mLocationManager.getLastKnownLocation(mProvider);
//
//            if (location != null) {
//                onLocationChanged(location);
//            }
//        }
//            mLocationManager.requestLocationUpdates(mProvider, 0, 5, this);
//        } else {
//            if(mConnection.isAuthenticated()) {
//                Presence p = new Presence(Presence.Type.available);
//                p.setStatus("Not available");
//                p.setPriority(100);
//                p.setMode(Presence.Mode.xa);
//                try {
//                    mConnection.sendPacket(p);
//                } catch (SmackException.NotConnectedException e) {
//                    e.printStackTrace();
//                }
//            }
//        }
        IntentFilter broadcastFilter = new IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION);
        broadcastFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        broadcastFilter.addAction(Intent.ACTION_SCREEN_ON);
        broadcastFilter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(br, broadcastFilter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(intent != null){
            if(intent.getAction() != null) {
                if(intent.getAction().equals(ACTION_REGISTER)) {
                    String phoneNr = intent.getStringExtra(EXTRA_PHONE_NUMBER);
                    Log.d("Phone Number", phoneNr);

                    mUserName = "FMF_" + phoneNr;
                    mPassword = generatePassword();
                    Log.d(LOG_TAG,"Generated Password: "+ mPassword);

                    Integer result = -1;
                    try {
                        result = mConnectionTask.get();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } catch (ExecutionException e) {
                        e.printStackTrace();
                    }
                    if(result.equals(CONNECTED)) {
                        boolean registrationSuccessful = createJabberAccount(mUserName, mPassword);
                        if(registrationSuccessful){
                            if(ContactListActivity.D) Log.d(LOG_TAG,"Account creation successful");
                        } else {
                            if(ContactListActivity.D) Log.d(LOG_TAG, "Account creation failed!");
                        }
                        LocalBroadcastManager.getInstance(this).sendBroadcast(
                                new Intent(RegistrationActivity.ACTION_PROCESS_JABBER_REGISTRATION_RESULT)
                                .putExtra(EXTRA_REGISTRATION_SUCCESSFUL, registrationSuccessful));
                    } else {
                        if(ContactListActivity.D) Log.d(LOG_TAG, "Connect to server failed!");
                        LocalBroadcastManager.getInstance(this).sendBroadcast(
                                new Intent(RegistrationActivity.ACTION_PROCESS_JABBER_REGISTRATION_RESULT)
                                        .putExtra(EXTRA_REGISTRATION_SUCCESSFUL, false));
                    }
                } else if(intent.getAction().equals(ACTION_UNREGISTER)) {
                    try {
                        AccountManager.getInstance(mConnection).deleteAccount();
                        if(ContactListActivity.D) Log.d(LOG_TAG, "ACCOUNT DELETED.");
                        Toast.makeText(this,"Account erfolgreich gelöscht.",Toast.LENGTH_LONG).show();
                        SharedPreferences pref = getSharedPreferences("FMFNumbers", Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = pref.edit();
                        editor.clear();
                        editor.commit();
                        LocalBroadcastManager.getInstance(this).sendBroadcast(
                                new Intent(ContactListActivity.ACTION_CLOSE)
                        );
                    } catch (SmackException.NoResponseException e) {
                        e.printStackTrace();
                        Toast.makeText(this,"Fehler bei Account Löschung. Keine Antwort vom Server",Toast.LENGTH_LONG).show();
                    } catch (XMPPException.XMPPErrorException e) {
                        e.printStackTrace();
                        Toast.makeText(this,"Fehler bei Account Löschung. Protokollfehler.",Toast.LENGTH_LONG).show();
                    } catch (SmackException.NotConnectedException e) {
                        e.printStackTrace();
                        Toast.makeText(this,"Fehler bei Account Löschung. Keine Serververbindung!",Toast.LENGTH_LONG).show();
                    }
                } else if(intent.getAction().equals(ACTION_CANCEL_NOTIFICATION)) {
                    int notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0);
                    if (notificationId != 0) {
//                        mNotificationManager.cancel(notificationId);
                        mExistingNotifications.remove(notificationId);
                    }
                } else if(intent.getAction().equals(ACTION_PROCESS_REQUEST_RESULT)) {
                    if (intent.getBooleanExtra(EXTRA_REQUEST_ACCEPTED, false))
                        sendAccept(intent.getStringExtra(EXTRA_JABBER_ID));
                    else sendDecline(intent.getStringExtra(EXTRA_JABBER_ID));
                } else if(intent.getAction().equals(ACTION_PROCESS_DB_REGISTRATION_RESULT)) {
                    boolean phoneNrAdded = intent.getBooleanExtra(RegistrationActivity.EXTRA_PHONE_NR_ADDED_TO_DB,false);
                    if(phoneNrAdded) {
                        SharedPreferences sharedPref = getSharedPreferences("FMFNumbers", Context.MODE_PRIVATE);

                        SharedPreferences.Editor user_pass_editor = sharedPref.edit();
                        user_pass_editor.putString(USERNAME, mUserName);
                        user_pass_editor.putString(PASSWORD, mPassword);
                        user_pass_editor.commit();

                        tryToLogIn();
                    } else stopSelf();
                } else if(intent.getAction().equals(ACTION_SEND_STOP)) {
                    if(ContactListActivity.D)
                        Log.d(LOG_TAG,"Sending stop to " +
                                mContactsAdapter.resolveJabberIdToRealName(mReceiverChat.getParticipant().split("/")[0]));
                    //TODO maybe remove this try/catch block
                    try {
                        if(mReceiverChat != null) mReceiverChat.sendMessage("S");
                    } catch (XMPPException e) {
                        e.printStackTrace();
                    } catch (SmackException.NotConnectedException e) {
                        e.printStackTrace();
                    }
                    try {
                        Presence p = new Presence(Presence.Type.available,"Available",100, Presence.Mode.available);
                        mConnection.sendPacket(p);
                        mCurrentPresenceStatus = p.getStatus();
                        if(ContactListActivity.D) Log.d(LOG_TAG,"sent presence after map closed: " + p);
                    } catch (SmackException.NotConnectedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        if(ContactListActivity.D) Log.d(LOG_TAG,"Service started");

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        //disconnect
        //remove Location Service
        unregisterReceiver(br);
    }

    //TODO replace this function by creating a map<jabberId,Chat>
//    private Chat findSenderChat(String jabberId){
//        if(mSenderChats != null)
//        {
//            for(Chat chat : mSenderChats)
//            {
//                if(chat.getParticipant().split("/")[0].equals(jabberId)) return chat;
//            }
//        }
//        return null;
//    }

    private boolean createJabberAccount(String user, String pass) {
        try {
            if(AccountManager.getInstance(mConnection).supportsAccountCreation()) {
                AccountManager.getInstance(mConnection).createAccount(user, pass);
                return true;
            } else Log.d(LOG_TAG,"SERVER DOESN'T SUPPORT ACCOUNT CREATION!");
        } catch (SmackException.NoResponseException e) {
            Log.e(LOG_TAG,e.getClass().getSimpleName());
            e.printStackTrace();
        } catch (XMPPException.XMPPErrorException e) {
            Log.e(LOG_TAG,e.getClass().getSimpleName());
            e.printStackTrace();
        } catch (SmackException.NotConnectedException e) {
            Log.e(LOG_TAG,e.getClass().getSimpleName());
            e.printStackTrace();
        }
        return false;
    }

    //TODO change back to private after test
    public void notifyAboutRequest(String jabberId, String fullName){
        if(ContactListActivity.isActive || MapsActivity.isActive)
        {
            Intent i = new Intent(ContactListActivity.ACTION_SHOW_REQUEST_DIALOG)
                    .putExtra(EXTRA_JABBER_ID,jabberId)
                    .putExtra(EXTRA_FULL_NAME,fullName);
            LocalBroadcastManager.getInstance(FMFCommunicationService.this).sendBroadcast(i);
        }
        else
        {
            mJabberId = jabberId;
            mFullNameFromNotificationId.put(1337, fullName);

            NotificationCompat.Builder ncb = new NotificationCompat.Builder(this)
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setAutoCancel(true)
                    .setDefaults(Notification.DEFAULT_ALL)
                    .setContentTitle(getText(R.string.title_position_request))
                    .setContentText("von: " + fullName);

            Intent resultIntent = new Intent(this,ContactListActivity.class);
            resultIntent.setAction(ContactListActivity.ACTION_SHOW_REQUEST_DIALOG)
                    .putExtra(EXTRA_JABBER_ID, jabberId);
//                    .putExtra(EXTRA_FULL_NAME, fullName);

            PendingIntent resPI = PendingIntent.getActivity(
                    this, 1337, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            ncb.setContentIntent(resPI);

            Intent deleteIntent = new Intent(this, FMFCommunicationService.class)
                    .setAction(ACTION_CANCEL_NOTIFICATION)
                    .putExtra(EXTRA_NOTIFICATION_ID, 1337);

            PendingIntent delPI = PendingIntent.getService(
                    this, 0, deleteIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            ncb.setDeleteIntent(delPI);

            mNotificationManager.notify(1337,ncb.build());
            mExistingNotifications.add(1337);
        }
    }

    public void notifyAboutAccept(String fullName) {
        if (ContactListActivity.isActive && mIsInteractive) {
            sendMapPresenceUpdate();
            Intent mapIntent = new Intent(this, MapsActivity.class);
            mapIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(mapIntent);
        } else {
            if(mAcceptedNotificationBuilder == null)
                mAcceptedNotificationBuilder = new NotificationCompat.Builder(this);
            mAcceptedNotificationBuilder
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setAutoCancel(true)
                    .setDefaults(Notification.DEFAULT_ALL)
                    .setContentTitle(getText(R.string.title_request_accepted))
                    .setContentText("von: " + fullName);

            Intent resultIntent = new Intent(this, ContactListActivity.class);
            resultIntent.setAction(ContactListActivity.ACTION_OPEN_MAP);

            PendingIntent resPI = PendingIntent.getActivity(
                    this, 1338, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            mAcceptedNotificationBuilder.setContentIntent(resPI);

            Intent deleteIntent = new Intent(this, FMFCommunicationService.class)
                    .setAction(ACTION_CANCEL_NOTIFICATION)
                    .putExtra(EXTRA_NOTIFICATION_ID, 1338);

            PendingIntent delPI = PendingIntent.getService(
                    this, 0, deleteIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            mAcceptedNotificationBuilder.setDeleteIntent(delPI);

            mNotificationManager.notify(1338, mAcceptedNotificationBuilder.build());
            mExistingNotifications.add(1338);
        }
    }

    public void notifyAboutDecline(String fullName) {
        if (ContactListActivity.isActive) {
            Intent i = new Intent(ContactListActivity.ACTION_SHOW_DECLINE_DIALOG);
            i.putExtra(EXTRA_FULL_NAME, fullName);
            LocalBroadcastManager.getInstance(this).sendBroadcast(i);
        } else {
            mFullNameFromNotificationId.put(1339, fullName);

            NotificationCompat.Builder ncb = new NotificationCompat.Builder(this)
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setAutoCancel(true)
                    .setDefaults(Notification.DEFAULT_ALL)
                    .setContentTitle(getText(R.string.title_request_declined))
                    .setContentText("von: " + fullName);

            Intent resultIntent = new Intent(this, FMFCommunicationService.class)
                    .setAction(ACTION_CANCEL_NOTIFICATION)
                    .putExtra(EXTRA_NOTIFICATION_ID, 1339);

            PendingIntent resPI = PendingIntent.getService(
                    this, 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            ncb.setContentIntent(resPI).setDeleteIntent(resPI);
            mNotificationManager.notify(1339, ncb.build());
            mExistingNotifications.add(1339);
        }
    }

    private String phoneNumberToJabberId(String phoneNumber) {
        return "fmf_" + phoneNumber + "@" + XMPP_SERVER;
    }

    private String jabberIdToPhoneNumber(String jabberId){
        String jabberIdSplit[] = jabberId.split("@");
        String phoneNumber = null;
        if(jabberIdSplit.length == 2)
        {
            phoneNumber = jabberIdSplit[0].substring(4);
        }
        return phoneNumber;
    }

    private MessageListener mChatMessageListener = new MessageListener() {

        @Override
        public void processMessage(Chat chat, Message message) {
            if(message.getFrom().split("/")[0].equals(chat.getParticipant().split("/")[0])) {
                String jabberId = chat.getParticipant().split("/")[0];
                String fullName = mContactsAdapter.resolveJabberIdToRealName(jabberId);
                if(fullName == null) fullName = jabberIdToPhoneNumber(jabberId);
                LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
                if(message.getBody().equals("P")) {
                    //Position request
                    notifyAboutRequest(jabberId, fullName);
                } else if(message.getBody().equals("N")) {
                    //Position request rejected
                    notifyAboutDecline(fullName);
                } else if(message.getBody().equals("S")) {
                    //TODO remove if presenceStatus change works
                    //Stop sending location updates to sender by removing him from the ArrayList
                    if(mAcceptedJabberIds != null) {
                        mAcceptedJabberIds.remove(jabberId);
                        if(mAcceptedJabberIds.isEmpty())
                            lm.removeUpdates(FMFCommunicationService.this);
                    }
                } else if(message.getBody().equals("A")) {
                    notifyAboutAccept(fullName);
                } else {
                    // Lat Lon coordinates should be received
                    String strLatLng[] = message.getBody().split(":");
                    if(strLatLng.length == 2) // Lat Lon coordinates received
                    {
                        double dLatLng[] = new double[2];
                        boolean isPosition;
                        try {
                            dLatLng[0] = Double.parseDouble(strLatLng[0]);
                            dLatLng[1] = Double.parseDouble(strLatLng[1]);
                            isPosition = true;
                        } catch (NumberFormatException e) {
                            isPosition = false;
                        }
                        if(isPosition) {
                            Log.d(LOG_TAG,"LOCATION RECEIVED: lat: " + dLatLng[0] + "lon: " + dLatLng[1]);
                            Intent i = new Intent(MapsActivity.UPDATE_FRIEND_LOCATION)
                                    .putExtra(EXTRA_FRIEND_LOCATION,dLatLng);
                            LocalBroadcastManager.getInstance(FMFCommunicationService.this).sendBroadcast(i);
                        }
                    }
                }
            }
        }
    };

    private void tryToLogIn() {
        if(!mConnection.isConnected())
            mConnectionTask = new XMPPConnectionTask().execute(CONNECT);
        else if(!mConnection.isAuthenticated() && !mUserName.isEmpty() && !mPassword.isEmpty())
            new XMPPConnectionTask().execute(LOGIN);
    }

    private void sendCurrentPresence() {
        if(mConnection.isAuthenticated() && mConnection.isConnected()) {
            Presence p = new Presence(Presence.Type.available);
            p.setPriority(100);
            if(MapsActivity.isActive && isProviderAvailable()) p.setStatus("Available:Map");
            else if(isProviderAvailable()) p.setStatus("Available");
            else p.setStatus("Not available");
            try {
                if(ContactListActivity.D) Log.d(LOG_TAG,"sending current presence: " + p);
                mConnection.sendPacket(p);
                mCurrentPresenceStatus = p.getStatus();
            } catch (SmackException.NotConnectedException e) {
                e.printStackTrace();
            }
        }
    }

    private void loadContacts() {
        if(mConnection.isAuthenticated() && !mLoadContactsTaskIsRunning) {
            new LoadAllContacts().execute();
        }
    }

    private void sendLocationUpdate(String jabberId) {
        Chat chat = mSenderChats.get(jabberId);
        if(chat != null) try {
            chat.sendMessage(mCurrentLocation);
        } catch (XMPPException e) {
            e.printStackTrace();
        } catch (SmackException.NotConnectedException e) {
            e.printStackTrace();
        }
    }

    private void checkForActiveNetworks() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        mActiveNetworkInfo = cm.getActiveNetworkInfo();
    }

    private String generatePassword()
    {
        //generate a 4 digit integer 1000 <10000
        int randomPIN = (int)(Math.random()*90000)+10000;
        //Store integer in a string
        return "fmi"+String.valueOf(randomPIN);

    }

//    private void fillInJabberIdAndStatus() {
//        if(mConnection.isAuthenticated()) {
//            Collection<RosterEntry> rosterEntries = mConnection.getRoster().getEntries();
//
//            for(RosterEntry rosterEntry: rosterEntries){
//                Presence p = mConnection.getRoster().getPresence(rosterEntry.getUser());
//                if(p.getType() == Presence.Type.available)
//                    mContactsAdapter.add(new FMFListEntry(rosterEntry.getUser(),FMFListEntry.ONLINE));
//                else
//                    mContactsAdapter.add(new FMFListEntry(rosterEntry.getUser(),FMFListEntry.OFFLINE));
//            }
//        }
//    }

//    public void connect(final String username, final String password) {
//
//        String[] userAndHost = username.split("@");
//        if(userAndHost.length != 2)
//        {
//            if(ContactListActivity.D)
//                Log.d(LOG_TAG, "Incorrect username. Cannot connect to Server.");
//            return;
//        }
//        final String host = userAndHost[1];
//
//        final ProgressDialog dialog = ProgressDialog.show(this,
//                "Connecting...", "Please wait...", false);
//
//        Thread t = new Thread(new Runnable() {
//
//            @Override
//            public void run() {
//                // Create a connection
//                ConnectionConfiguration connConfig = new ConnectionConfiguration(
//                        host, 5222, "FMF");
//                //connConfig.setSecurityMode(ConnectionConfiguration.SecurityMode.enabled);
//                XMPPConnection connection = new XMPPTCPConnection(connConfig);
//
//                try {
//                    connection.connect();
//
//                } catch (XMPPException ex) {
//                    Log.e(LOG_TAG, "Failed to connect to "
//                            + connection.getHost());
//                    Log.e(LOG_TAG, ex.toString());
//                    setConnection(null);
//                } catch (SmackException e) {
//                    // TODO Auto-generated catch block
//                    e.printStackTrace();
//                } catch (IOException e) {
//                    // TODO Auto-generated catch block
//                    e.printStackTrace();
//                }
//                try {
//                    // SASLAuthentication.supportSASLMechanism("PLAIN", 0);
//                    connection.login(username, password);
//                    Log.i(LOG_TAG,
//                            "Connected to " + connection.getServiceName());
//                    Log.i(LOG_TAG,
//                            "Logged in as " + connection.getUser());
//                    //addRosterListener(connection);
//
//                    //TODO (Martin): send Presence unavailable when mProvider == null
//                    // Set the status to available
//                    Presence presence = new Presence(Presence.Type.available);
//                    presence.setMode(Presence.Mode.available);
//                    presence.setStatus("Online");
////					connection.sendPacket(presence);
//
//                    setConnection(connection);
//
////                    Intent i = new Intent(INFO_CONNECTED);
////                    LocalBroadcastManager.getInstance(FMFCommunicationService.this).sendBroadcast(i);
//
//                    fillInJabberIdAndStatus();
//
////                    if(mConnection != null)
////                        mConnection
////                                .getRoster()
////                                .addRosterListener(new FMFRosterListener(mContactListEntries,mRosterMap));
//
//                    //TODO (Martin): remove this code sequence
//                    Collection<RosterEntry> entries = connection.getRoster().getEntries();
//                    for (RosterEntry entry : entries) {
//                        Log.d(LOG_TAG,
//                                "--------------------------------------");
//                        Log.d(LOG_TAG, "RosterEntry " + entry);
//                        Log.d(LOG_TAG,
//                                "User: " + entry.getUser());
//                        Log.d(LOG_TAG,
//                                "Name: " + entry.getName());
//                        Log.d(LOG_TAG,
//                                "Status: " + entry.getStatus());
//                        Log.d(LOG_TAG,
//                                "Type: " + entry.getType());
//                        Presence entryPresence = connection.getRoster().getPresence(entry
//                                .getUser());
//
//                        Log.d(LOG_TAG, "Presence Status: "
//                                + entryPresence.getStatus());
//                        Log.d(LOG_TAG, "Presence Type: "
//                                + entryPresence.getType());
//                        Presence.Type type = entryPresence.getType();
//                        if (type == Presence.Type.available)
//                            Log.d(LOG_TAG, "Presence AVIALABLE");
//                        Log.d(LOG_TAG, "Presence : "
//                                + entryPresence);
//
//                    }
//
//                } catch (XMPPException ex) {
//                    Log.e(LOG_TAG, "Failed to log in as "
//                            + username);
//                    Log.e(LOG_TAG, ex.toString());
//                    setConnection(null);
//                } catch (SmackException e) {
//                    // TODO Auto-generated catch block
//                    Log.e(LOG_TAG, e.toString());
//                    e.printStackTrace();
//                } catch (IOException e) {
//                    // TODO Auto-generated catch block
//                    e.printStackTrace();
//                }
//
//                dialog.dismiss();
//            }
//        });
//        t.start();
//        dialog.show();
//    }

    @Override
    public void onLocationChanged(Location location) {
        mCurrentLocation = location.getLatitude()+":"+location.getLongitude();
        if(ContactListActivity.D) Log.d(LOG_TAG,"onLocationChanged: " + mCurrentLocation);
        if(mAcceptedJabberIds != null)
        {
            for(Map.Entry<String,Boolean> jIdMap : mAcceptedJabberIds.entrySet())
            {
                if(jIdMap.getValue().equals(true)) sendLocationUpdate(jIdMap.getKey());
            }
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        if(ContactListActivity.D) Log.d(LOG_TAG,
                "onStatusChanged: Provider: "+ provider + " status: " + status);

        Presence p = new Presence(Presence.Type.available);
        if(status == LocationProvider.OUT_OF_SERVICE) p.setStatus("Not available");
        else if(MapsActivity.isActive) p.setStatus("Available:Map");
        else p.setStatus("Available");

        if(mConnection.isAuthenticated() && mConnection.isConnected() &&
                !mCurrentPresenceStatus.equals(p.getStatus())) {
            try {
                if(ContactListActivity.D) Log.d(LOG_TAG,"sending presence: " + p);
                mConnection.sendPacket(p);
                mCurrentPresenceStatus = p.getStatus();
            } catch (SmackException.NotConnectedException e) {
                e.printStackTrace();
            }
        }

    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onProviderDisabled(String provider) {
        //TODO (Martin): this might be obsolete because we're receiving PROVIDERS_CHANGED_ACTION

        //TODO send Presence packet
        if(ContactListActivity.D) Log.d(LOG_TAG,"onProviderDisabled");
//        if(provider.equals(mProvider))
//        {
//            mLocationManager.removeUpdates(this);
//            // Creating a criteria object to retrieve provider
//            Criteria criteria = new Criteria();
//            criteria.setAccuracy(Criteria.ACCURACY_FINE);
//            // Getting the name of the best provider
//            mProvider = mLocationManager.getBestProvider(criteria, true);
//
//            // Getting Current Location From GPS
//            if(mProvider != null)
//                mLocationManager.requestLocationUpdates(mProvider, 0, 5, this);
//        }
    }

    /**
     * Background Async Task to Load all product by making HTTP Request
     * */
    class LoadAllContacts extends AsyncTask<String, String, Collection<String> > {

        // url to get all products list
        private String url_all_numbers = "http://farahzeb.de/fmi/get_all_numbers.php";

        // JSON Node names
        private static final String TAG_SUCCESS = "success";
        private static final String TAG_PRODUCTS = "registeredNumbers";
        private static final String TAG_NUMBER = "registeredNumber";

        // products JSONArray
        JSONArray contacts = null;
        /**
         * Before starting background thread Show Progress Dialog
         * */
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            mLoadContactsTaskIsRunning = true;
        }

        /**
         * getting All products from url
         * */
        protected Collection<String> doInBackground(String... args) {

            ArrayList<String> ret = new ArrayList<String>();
            // Building Parameters
            List<NameValuePair> params = new ArrayList<NameValuePair>();
            // getting JSON string from URL
            JSONParser jParser = new JSONParser();
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
                    String myNumber = sharedPref.getString(USERNAME,"").substring(4);
//                    myNumber = jabberIdToPhoneNumber(myNumber);

                    // looping through All Products
                    for (int i = 0; i < contacts.length(); i++) {
                        JSONObject c = contacts.getJSONObject(i);

                        // Storing each json item in variable
//                        String id = c.getString(TAG_RID);
                        String number = c.getString(TAG_NUMBER);

                        //getting all registered numbers and searching for their contactname in phone
                        // own number shouldnt be in the contact list activity

                        if (!number.equals(myNumber)) {
                            String contactname = getContactName(FMFCommunicationService.this, number);

                            // adding jabber ID and contact name to ArrayList
                            if(contactname != null){
                                String jabberId = phoneNumberToJabberId(number);
                                ret.add(jabberId + ":::" + contactname);
                            }
                        }
                    }
                } else {
                    // no contacts found
                    Log.d("no contacts","no contacts found");
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }

            return ret;
        }

        /**
         * After completing background task Dismiss the progress dialog
         * **/
        protected void onPostExecute(Collection<String> contactData) {

//            mContacts = new ArrayList<FMFListEntry>();
            ArrayList<FMFListEntry> newListEntries = new ArrayList<FMFListEntry>();
            for(String contact: contactData)
            {
                String contactDataSplit[] = contact.split(":::");

                FMFListEntry listEntry = new FMFListEntry(contactDataSplit[0],contactDataSplit[1]);

//                mContactsAdapter.add(new FMFListEntry(contactDataSplit[0],contactDataSplit[1]));
//                RosterEntry roster = mConnection.getRoster().getEntry(contactDataSplit[0]);
//                if(roster == null){
                    try {
                        mConnection.getRoster().createEntry(contactDataSplit[0],contactDataSplit[1],null);
                    } catch (SmackException.NotLoggedInException e) {
                        e.printStackTrace();
                    } catch (SmackException.NoResponseException e) {
                        e.printStackTrace();
                    } catch (XMPPException.XMPPErrorException e) {
                        e.printStackTrace();
                    } catch (SmackException.NotConnectedException e) {
                        e.printStackTrace();
                    }
//                } else if (roster.getName() == null)
//                    try {
//                        roster.setName(contactDataSplit[1]);
//                    } catch (SmackException.NotConnectedException e) {
//                        e.printStackTrace();
//                    }
//                else if(roster.getName().isEmpty())
//                    try {
//                        roster.setName(contactDataSplit[1]);
//                    } catch (SmackException.NotConnectedException e) {
//                        e.printStackTrace();
//                    }
                Presence p = mConnection.getRoster().getPresence(listEntry.jabberID);
                if(ContactListActivity.D) Log.d(LOG_TAG,
                        "Presence from: " + p.getFrom() + " " + p );
                if(p.getStatus() != null) {
                    if(p.getStatus().contains("Available")) listEntry.status = FMFListEntry.ONLINE;
                }
                if(mContactsAdapter != null) {
                    if(!mContactsAdapter.contains(contactDataSplit[0]))
                        newListEntries.add(listEntry);
                }

//                mContacts.add(listEntry);
            }
            final ArrayList<FMFListEntry> finalNewListEntries = newListEntries;
            if(mContactsAdapter != null ) mUpdateListViewHandler.post(new Runnable() {
                @Override
                public void run() {
                    mContactsAdapter.addAll(finalNewListEntries);
                }
            });

            mChatManager = ChatManager.getInstanceFor(mConnection);
            if(mChatManager != null) mChatManager.addChatListener( new ChatManagerListener() {
                @Override
                public void chatCreated(Chat chat, boolean createdLocally) {
                    if(!createdLocally)
                    {
                        chat.addMessageListener(mChatMessageListener);

                        boolean chatAlreadyExists = false;
                        if(mSenderChats == null)
                        {
                            mSenderChats = new HashMap<String, Chat>();
                        } else {
                            for(Chat aChat : mSenderChats.values())
                            {
                                if( aChat.getParticipant().equals(chat.getParticipant()) )
                                    chatAlreadyExists = true;
                            }
                        }
                        if(!chatAlreadyExists)
                            mSenderChats.put(chat.getParticipant().split("/")[0],chat);
                    }
                }
            });

            mLoadContactsTaskIsRunning = false;

            // Add a packet listener to get messages sent to us
//            PacketFilter filter = new PacketTypeFilter(Presence.class);
//            mConnection.addPacketListener(new PacketListener() {
//                @Override
//                public void processPacket(Packet packet) {
//                    Presence p = (Presence) packet;
//                    if (ContactListActivity.D) Log.d(LOG_TAG, "received presence: " + p.toString());
//                }
//            },filter);
//                    if (message.getBody() != null) {
//                        if (message.getBody().equals("P")) {
//                            //Position request
//                        }
//                        String fromName = StringUtils.parseBareAddress(message
//                                .getFrom());
//                        Log.i(LOG_TAG, "Text Received " + message.getBody()
//                                + " from " + fromName);
////						messages.add(fromName + ":");
////						messages.add(message.getBody());
////						// Add the incoming message to the list view
////						mHandler.post(new Runnable() {
////							public void run() {
////								setListAdapter();
////							}
////						});
//                    }
//                }
//            }, filter);

            // dismiss the dialog after getting all products
//            pDialog.dismiss();
            // updating UI from Background Thread
//            runOnUiThread(new Runnable() {
//
//                public void run() {
//                    /**
//                     * Updating parsed JSON data into ListView
//                     * */
//                    ListAdapter adapter = new SimpleAdapter(
//                            ContactListActivity.this, registeredList,
//                            R.layout.single_contact, new String[]{TAG_RID,
//                            TAG_NUMBER},
//                            new int[]{R.id.rid, R.id.contact_number});
//                    // updating listview
//                    ListView lv = (ListView) findViewById(R.id.contact_list);
//                    lv.setAdapter(adapter);
//                }
//            });

        }
        private String getContactName(Context context, String phoneNumber) {
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

    private class XMPPConnectionTask extends AsyncTask<Integer,Void,Integer> {

        @Override
        protected Integer doInBackground(Integer... params) {
            if(params[0].equals(CONNECT)) {
                try {
                    mConnection.connect();
                    return CONNECTED;
                } catch (SmackException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (XMPPException e) {
                    e.printStackTrace();
                }
                return  NOT_CONNECTED;
            } else if(params[0].equals(LOGIN)) {
                try {
                    mConnection.getRoster().addRosterListener(FMFCommunicationService.this);
                    mConnection.login(mUserName, mPassword);
                    return LOGGED_IN;
                } catch (XMPPException e) {
                    e.printStackTrace();
                } catch (SmackException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return  NOT_LOGGED_IN;
            }
            return  -1;
        }

        @Override
        protected void onPostExecute(Integer result) {
            super.onPostExecute(result);

            if(result.equals(CONNECTED)) {
                tryToLogIn();
            }
            if(result.equals(LOGGED_IN)) {
                sendCurrentPresence();
                loadContacts();
            }
        }
    }

    @Override
    public void entriesAdded(Collection<String> strings) {
        //TODO Martin do a database lookup to find out if this is a FMF user
        //TODO if yes, add to contact list, otherwise unsubscribe that user
        loadContacts();
    }

    @Override
    public void entriesUpdated(Collection<String> strings) {
        //may change the name in the listView
    }

    @Override
    public void entriesDeleted(Collection<String> strings) {
        //Nothing to do
    }

    @Override
    public void presenceChanged(Presence presence) {
        final String fromJabberId = presence.getFrom().split("/")[0];

        if(ContactListActivity.D) Log.d(LOG_TAG,"received presence update: "+ presence);
        boolean updatePresence = false;
        if(presence.getType().equals(Presence.Type.unavailable)) updatePresence = true;

        boolean presenceUpdate = FMFListEntry.OFFLINE;
        if(presence.getStatus() != null)
        {
            if(presence.getStatus().contains("Map") && mAcceptedJabberIds != null) {
                if(mAcceptedJabberIds.containsKey(fromJabberId))
                    mAcceptedJabberIds.put(fromJabberId,true);
                if(mCurrentLocation != null) sendLocationUpdate(fromJabberId);
            } else if(mAcceptedJabberIds != null) mAcceptedJabberIds.remove(fromJabberId);

            if(presence.getStatus().contains("Available")){
                updatePresence = true;
                presenceUpdate = FMFListEntry.ONLINE;
            } else if(  presence.getStatus().equals("Not available") ||
                        presence.getType().equals(Presence.Type.unavailable))
                updatePresence = true;
        }

        final boolean finalPresenceUpdate = presenceUpdate;
        if(updatePresence)
            mUpdateListViewHandler.post(new Runnable() {
                @Override
                public void run() {
                    mContactsAdapter.setStatusByJabberId(fromJabberId,finalPresenceUpdate);
                }
            });
    }

}
