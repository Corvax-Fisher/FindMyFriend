package com.example.fmi_fmf;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.util.ArrayMap;
import android.util.Log;
import android.widget.Toast;

import org.jivesoftware.smack.Chat;
import org.jivesoftware.smack.ChatManager;
import org.jivesoftware.smack.ChatManagerListener;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.MessageListener;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.RosterEntry;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.MessageTypeFilter;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

public class FMFCommunicationService extends Service implements LocationListener {

    private final String LOG_TAG = FMFCommunicationService.class.getSimpleName();

    private final long REQUEST_TIMEOUT = 10000L; // 10 seconds

    public static final String EXTRA_SEND_STOP = "send stop";

    public static final String EXTRA_FRIEND_LOCATION = "friend location";

    public static final String ACTION_REGISTER = "register";
    public static final String EXTRA_PHONE_NUMBER = "phone number";

    public static final String ACTION_CANCEL_NOTIFICATION = "cancel accept notification";
    public static final String EXTRA_NOTIFICATION_ID = "notification id";

    public static final String EXTRA_JABBER_ID = "jabber id";
    public static final String EXTRA_FULL_NAME = "full name";

    public static final String ACTION_PROCESS_REQUEST_RESULT = "process request result";
    public static final String EXTRA_REQUEST_ACCEPTED = "request accepted";

//    public static final String INFO_CONNECTED = "connected info";
//
//    public static final String ACTION_ACCEPT_NOTIFICATION_CANCELED = "accept notification canceled";

    public enum RET_CODE {OK, NO_PROVIDER, NOT_CONNECTED};
//    public enum N_INFO {NONE, REQUEST, ACCEPT};
//    private N_INFO mNotificationInfo;

    private LocationManager mLocationManager;
    private XMPPConnection mConnection;
    private ChatManager mChatManager;
    private Chat mReceiverChat;
    private ArrayList<Chat> mSenderChats;
    private ArrayList<String> mAcceptedJabberIds;
    private String mProvider;
    private NotificationManager mNotificationManager;

    private ContactListAdapter mContactsAdapter;

    private String mJabberId;
    private Map<Integer,String> mFullNameFromNotificationId;
    private NotificationCompat.Builder mAcceptedNotificationBuilder;
//    private boolean mAcceptNotificationExists = false;
//
//    private ArrayList<FMFListEntry> mContactListEntries;
//    private Map<String,Integer> mRosterMap;
    private Set<Integer> mExistingNotifications;

    // Binder given to clients
    private final IBinder mBinder = new LocalBinder();
    private final TimerTask mRequestTimerTask = new TimerTask() {
        @Override
        public void run() {
            Intent i = new Intent(ContactListActivity.ACTION_CANCEL_WAIT_PROGRESS);
            LocalBroadcastManager.getInstance(FMFCommunicationService.this).sendBroadcast(i);
        }
    };
    private String mCurrentLocation;

    private BroadcastReceiver br = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction().equals(LocationManager.PROVIDERS_CHANGED_ACTION))
            {
                if(ContactListActivity.D)
                    Toast.makeText(FMFCommunicationService.this,"Providers changed",Toast.LENGTH_SHORT).show();
                // Creating a criteria object to retrieve provider
                Criteria criteria = new Criteria();
                criteria.setAccuracy(Criteria.ACCURACY_FINE);
                // Getting the name of the best provider
                String bestProvider = mLocationManager.getBestProvider(criteria, true);
                if(bestProvider == null && mProvider != null)
                    mLocationManager.removeUpdates(FMFCommunicationService.this);
                 else if(mProvider == null) {
                    if(bestProvider != null)
                        mLocationManager.requestLocationUpdates(bestProvider, 0, 5, FMFCommunicationService.this);
                } else if(!mProvider.equals(bestProvider)) {
                    mLocationManager.removeUpdates(FMFCommunicationService.this);
                    mLocationManager.requestLocationUpdates(bestProvider, 0, 5, FMFCommunicationService.this);
                }
                mProvider = bestProvider;
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

    //TODO remove onRebind an onUnbind

    @Override
    public void onRebind(Intent intent) {
        super.onRebind(intent);

        if(ContactListActivity.D)
            Log.d(FMFCommunicationService.class.getSimpleName(), "rebinding service...");

    }

    @Override
    public boolean onUnbind(Intent intent) {
        if(ContactListActivity.D)
            Log.d(FMFCommunicationService.class.getSimpleName(), "unbinding service...");

        return true; //super.onUnbind(intent);
    }

    /** methods for clients */
    public boolean isProviderAvailable() {
        return mProvider != null;
    }

//    public void addRosterListener(RosterListener rosterListener)
//    {
//        if(mConnection.isAuthenticated()) mConnection.getRoster().addRosterListener(rosterListener);
//    }

    public RET_CODE sendRequest(String myFriendsJabberId) {
        if(mProvider == null) return RET_CODE.NO_PROVIDER;
        if(!mConnection.isAuthenticated()) return RET_CODE.NOT_CONNECTED;
        if(ContactListActivity.D)
            Toast.makeText(this,"sending a request to " + myFriendsJabberId,Toast.LENGTH_SHORT).show();
        mReceiverChat = mChatManager.createChat(myFriendsJabberId,mChatMessageListener);
        try {
            mReceiverChat.sendMessage("P");
            Timer t = new Timer();
            t.schedule(mRequestTimerTask,REQUEST_TIMEOUT);
        } catch (XMPPException e) {
            e.printStackTrace();
        } catch (SmackException.NotConnectedException e) {
            e.printStackTrace();
            return RET_CODE.NOT_CONNECTED;
        }
        return RET_CODE.OK;
    }

    public void sendAccept(String myFriendsJabberId) {
        if(ContactListActivity.D)
            Toast.makeText(getApplicationContext(),"sending an accept to " + myFriendsJabberId,Toast.LENGTH_SHORT).show();

        if(mAcceptedJabberIds == null) mAcceptedJabberIds = new ArrayList<String>();
        mAcceptedJabberIds.add(myFriendsJabberId);
        Chat chat = findSenderChat(myFriendsJabberId);
        if(chat != null) try {
            chat.sendMessage("A");
        } catch (XMPPException e) {
            e.printStackTrace();
        } catch (SmackException.NotConnectedException e) {
            e.printStackTrace();
        }
    }

//    TODO remove this method
//    public void sendAcceptAndRequest(String myFriendsJabberId) {
//        if(ContactListActivity.D)
//            Toast.makeText(getApplicationContext(),"sending accept and request to " + myFriendsJabberId,Toast.LENGTH_SHORT).show();
//
//        sendAccept(myFriendsJabberId);
//        sendRequest(myFriendsJabberId);
//    }

    public void sendDecline(String myFriendsJabberId) {
        if(ContactListActivity.D)
            Toast.makeText(getApplicationContext(),"sending decline to " + myFriendsJabberId,Toast.LENGTH_SHORT).show();

        Chat chat = findSenderChat(myFriendsJabberId);
        if(chat != null) try {
            chat.sendMessage("N");
        } catch (XMPPException e) {
            e.printStackTrace();
        } catch (SmackException.NotConnectedException e) {
            e.printStackTrace();
        }
    }

//    public N_INFO getNotificationInfo(){
//        N_INFO ret = mNotificationInfo;
//        mNotificationInfo = N_INFO.NONE;
//        return ret;
//    }

    public ContactListAdapter getContactsAdapter() {
        return mContactsAdapter;
    }

    public String getFullName(int notificationId) { return mFullNameFromNotificationId.get(notificationId); }

    public String getJabberId() {
        return mJabberId;
    }

//    public void setAcceptNotificationCanceled() { mAcceptNotificationExists = false; }

    public void updateAcceptNotificationIfExists(boolean contactListActivityIsActive) {
        if(mExistingNotifications.contains(1338)) {
//            if(mNotificationInfo == N_INFO.NONE) mNotificationInfo = N_INFO.ACCEPT;
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
            mAcceptedNotificationBuilder.setContentIntent(resPI);
            mNotificationManager.notify(1338, mAcceptedNotificationBuilder.build());
        }
    }

    public void cancelNotification(int notificationId){
        mExistingNotifications.remove(notificationId);
    }

    public boolean notificationExists(int notificationId){
        return mExistingNotifications.contains(notificationId);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        if(ContactListActivity.D) Log.d(LOG_TAG,"Service created");

//        mNotificationInfo = N_INFO.NONE;
        mExistingNotifications = new HashSet<Integer>(3);
        mFullNameFromNotificationId = new ArrayMap<Integer, String>(2);
//        mExistingNotifications.add(1337);
//        mExistingNotifications.add(1338);

        SharedPreferences sharedPref = getSharedPreferences("credentials",Context.MODE_PRIVATE);
        String username = sharedPref.getString("username", "");
        String password = sharedPref.getString("password", "");
        if(!username.isEmpty() && !password.isEmpty())
        {
            connect(username, password);
        } else {
            //TODO: create register-activity
            //startActivity(new Intent(this,RegisterActivity.class));
        }
        mLocationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        //Add LocationListener

        // Creating a criteria object to retrieve provider
        Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_FINE);
        // Getting the name of the best provider
        mProvider = mLocationManager.getBestProvider(criteria, true);

        // Getting Current Location From GPS
        if(mProvider != null)
        {
            Location location = mLocationManager.getLastKnownLocation(mProvider);

            if(location!=null){
                onLocationChanged(location);
            }

            mLocationManager.requestLocationUpdates(mProvider, 0, 5, this);
        } /*else {
            mLocationManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER,this,null);
            mLocationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER,this,null);
        }*/
        registerReceiver(br, new IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if(intent != null){
            if(intent.getAction() != null) {
                if (intent.getAction().equals(ACTION_REGISTER)) {
                    String phoneNr = intent.getStringExtra(EXTRA_PHONE_NUMBER);
                    //TODO (Farah): register
                } else if(intent.getAction().equals(ACTION_CANCEL_NOTIFICATION)) {
                    int notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID,0);
                    if(notificationId != 0){
//                        mNotificationManager.cancel(notificationId);
                        mExistingNotifications.remove(notificationId);
                    }
                } else if(intent.getAction().equals(ACTION_PROCESS_REQUEST_RESULT)) {
                    if(intent.getBooleanExtra(EXTRA_REQUEST_ACCEPTED, false))
                        sendAccept(intent.getStringExtra(EXTRA_JABBER_ID));
                    else sendDecline(intent.getStringExtra(EXTRA_JABBER_ID));
                }
            }

            if(intent.hasExtra(EXTRA_SEND_STOP))
            {
                Toast.makeText(getApplicationContext(),"sending stop to "+intent.getStringExtra("send stop to"),Toast.LENGTH_SHORT).show();
                try {
                    if(mReceiverChat != null) mReceiverChat.sendMessage("S");
                } catch (XMPPException e) {
                    e.printStackTrace();
                } catch (SmackException.NotConnectedException e) {
                    e.printStackTrace();
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

    private Chat findSenderChat(String jabberId){
        if(mSenderChats != null)
        {
            for(Chat chat : mSenderChats)
            {
                if(chat.getParticipant().equals(jabberId)) return chat;
            }
        }
        return null;
    }
    //TODO change back to private after test
    public void notifyAboutRequest(String jabberId, String fullName){
        if(ContactListActivity.isActive)
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
                    .setContentTitle("Positionsanfrage")
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
//            mNotificationInfo = N_INFO.REQUEST;
        }
    }

    public void notifyAboutAccept(String fullName) {
        if (ContactListActivity.isActive) {
            Intent mapIntent = new Intent(this, MapsActivity.class);
            mapIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(mapIntent);
        } else {
            if(mAcceptedNotificationBuilder == null)
                mAcceptedNotificationBuilder = new NotificationCompat.Builder(this);
            mAcceptedNotificationBuilder
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setAutoCancel(true)
                    .setContentTitle("Anfrage angenommen")
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
//            mAcceptNotificationExists = true;
//            if(mNotificationInfo != N_INFO.REQUEST) mNotificationInfo = N_INFO.ACCEPT;
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
                    .setContentTitle("Anfrage abgelehnt")
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
        return "FMF" + phoneNumber + "@jabber.de";
    }

    private String jabberIdToPhoneNumber(String jabberId){
        String jabberIdSplit[] = jabberId.split("@");
        String phoneNumber = null;
        if(jabberIdSplit.length == 2)
        {
            phoneNumber = jabberIdSplit[0].substring(3);
        }
        return phoneNumber;
    }

    private MessageListener mChatMessageListener = new MessageListener() {

        @Override
        public void processMessage(Chat chat, Message message) {
            String fullName = mContactsAdapter.resolveJabberIdToRealName(chat.getParticipant());
            if(fullName == null) fullName = jabberIdToPhoneNumber(chat.getParticipant());
            if(message.getBody().equals("P")) {
                //Position request
                //TODO: resolve the real name from the jabberID
                notifyAboutRequest(chat.getParticipant(), fullName);
                mLocationManager.requestLocationUpdates(mProvider,0,5,FMFCommunicationService.this);
            } else if(message.getBody().equals("N")) {
                //Position request rejected
                notifyAboutDecline(fullName);
            } else if(message.getBody().equals("S")) {
                //Stop sending location updates to sender by removing him from the ArrayList
                mAcceptedJabberIds.remove(chat.getParticipant());
                if(mAcceptedJabberIds.isEmpty())
                    mLocationManager.removeUpdates(FMFCommunicationService.this);
            } else if(message.getBody().equals("A")) {
                mRequestTimerTask.cancel();
                notifyAboutAccept(fullName);
            } else {
                // Lat Lon coordinates should be received
                String strLatLng[] = message.getBody().split(":");
                if(strLatLng.length == 2) // Lat Lon coordinates received
                {
                    double dLatLng[] = {    Double.parseDouble(strLatLng[0]),
                                            Double.parseDouble(strLatLng[1])
                    };
                    Intent i = new Intent(MapsActivity.UPDATE_FRIEND_LOCATION)
                            .putExtra(EXTRA_FRIEND_LOCATION,dLatLng);
                    LocalBroadcastManager.getInstance(FMFCommunicationService.this).sendBroadcast(i);
                }
            }
        }
    };

    public void setConnection(XMPPConnection connection) {
        this.mConnection = connection;
        if (connection != null) {
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
                            mSenderChats = new ArrayList<Chat>();
                        } else {
                            for(Chat aChat : mSenderChats)
                            {
                                if( aChat.getParticipant().equals(chat.getParticipant()) )
                                    chatAlreadyExists = true;
                            }
                        }
                        if(!chatAlreadyExists)
                            mSenderChats.add(chat);
                    }
                }
            });
            // Add a packet listener to get messages sent to us
            PacketFilter filter = new MessageTypeFilter(Message.Type.chat);
            connection.addPacketListener(new PacketListener() {
                @Override
                public void processPacket(Packet packet) {
                    Message message = (Message) packet;
                    if (message.getBody() != null) {
                        if(message.getBody().equals("P")) {
                            //Position request
                        }
                        String fromName = StringUtils.parseBareAddress(message
                                .getFrom());
                        Log.i(LOG_TAG, "Text Received " + message.getBody()
                                + " from " + fromName);
//						messages.add(fromName + ":");
//						messages.add(message.getBody());
//						// Add the incoming message to the list view
//						mHandler.post(new Runnable() {
//							public void run() {
//								setListAdapter();
//							}
//						});
                    }
                }
            }, filter);

        }
    }

    private void fillInJabberIdAndStatus() {
        if(mConnection.isAuthenticated()) {
            mContactsAdapter = new ContactListAdapter(this,R.layout.contact_list_item,R.id.nameView);
            Collection<RosterEntry> rosterEntries = mConnection.getRoster().getEntries();

            for(RosterEntry rosterEntry: rosterEntries){
                Presence p = mConnection.getRoster().getPresence(rosterEntry.getUser());
                if(p.getType() == Presence.Type.available)
                    mContactsAdapter.add(new FMFListEntry(rosterEntry.getUser(),FMFListEntry.ONLINE));
                else
                    mContactsAdapter.add(new FMFListEntry(rosterEntry.getUser(),FMFListEntry.OFFLINE));
            }
        }
    }

    public void connect(final String username, final String password) {

        String[] userAndHost = username.split("@");
        if(userAndHost.length != 2)
        {
            if(ContactListActivity.D)
                Log.d(LOG_TAG, "Incorrect username. Cannot connect to Server.");
            return;
        }
        final String host = userAndHost[1];

        final ProgressDialog dialog = ProgressDialog.show(this,
                "Connecting...", "Please wait...", false);

        Thread t = new Thread(new Runnable() {

            @Override
            public void run() {
                // Create a connection
                ConnectionConfiguration connConfig = new ConnectionConfiguration(
                        host, 5222, "FMF");
                //connConfig.setSecurityMode(ConnectionConfiguration.SecurityMode.enabled);
                XMPPConnection connection = new XMPPTCPConnection(connConfig);

                try {
                    connection.connect();

                } catch (XMPPException ex) {
                    Log.e(LOG_TAG, "Failed to connect to "
                            + connection.getHost());
                    Log.e(LOG_TAG, ex.toString());
                    setConnection(null);
                } catch (SmackException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                try {
                    // SASLAuthentication.supportSASLMechanism("PLAIN", 0);
                    connection.login(username, password);
                    Log.i(LOG_TAG,
                            "Connected to " + connection.getServiceName());
                    Log.i(LOG_TAG,
                            "Logged in as " + connection.getUser());
                    //addRosterListener(connection);

                    //TODO (Martin): send Presence unavailable when mProvider == null
                    // Set the status to available
                    Presence presence = new Presence(Presence.Type.available);
                    presence.setMode(Presence.Mode.available);
                    presence.setStatus("Online");
//					connection.sendPacket(presence);

                    setConnection(connection);

//                    Intent i = new Intent(INFO_CONNECTED);
//                    LocalBroadcastManager.getInstance(FMFCommunicationService.this).sendBroadcast(i);

                    fillInJabberIdAndStatus();

//                    if(mConnection != null)
//                        mConnection
//                                .getRoster()
//                                .addRosterListener(new FMFRosterListener(mContactListEntries,mRosterMap));

                    //TODO (Martin): remove this code sequence
                    Collection<RosterEntry> entries = connection.getRoster().getEntries();
                    for (RosterEntry entry : entries) {
                        Log.d(LOG_TAG,
                                "--------------------------------------");
                        Log.d(LOG_TAG, "RosterEntry " + entry);
                        Log.d(LOG_TAG,
                                "User: " + entry.getUser());
                        Log.d(LOG_TAG,
                                "Name: " + entry.getName());
                        Log.d(LOG_TAG,
                                "Status: " + entry.getStatus());
                        Log.d(LOG_TAG,
                                "Type: " + entry.getType());
                        Presence entryPresence = connection.getRoster().getPresence(entry
                                .getUser());

                        Log.d(LOG_TAG, "Presence Status: "
                                + entryPresence.getStatus());
                        Log.d(LOG_TAG, "Presence Type: "
                                + entryPresence.getType());
                        Presence.Type type = entryPresence.getType();
                        if (type == Presence.Type.available)
                            Log.d(LOG_TAG, "Presence AVIALABLE");
                        Log.d(LOG_TAG, "Presence : "
                                + entryPresence);

                    }

                } catch (XMPPException ex) {
                    Log.e(LOG_TAG, "Failed to log in as "
                            + username);
                    Log.e(LOG_TAG, ex.toString());
                    setConnection(null);
                } catch (SmackException e) {
                    // TODO Auto-generated catch block
                    Log.e(LOG_TAG, e.toString());
                    e.printStackTrace();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

                dialog.dismiss();
            }
        });
        t.start();
        dialog.show();
    }

    @Override
    public void onLocationChanged(Location location) {
        mCurrentLocation = location.getLatitude()+":"+location.getLongitude();
        Toast.makeText(this,mCurrentLocation,Toast.LENGTH_SHORT).show();
        if(mAcceptedJabberIds != null)
        {
            Chat chat;
            for(String jId : mAcceptedJabberIds)
            {
                chat = findSenderChat(jId);
                if(chat != null) try {
                    chat.sendMessage(mCurrentLocation);
                } catch (XMPPException e) {
                    e.printStackTrace();
                } catch (SmackException.NotConnectedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        if(ContactListActivity.D)
            Toast.makeText(this,"onStatusChanged",Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onProviderDisabled(String provider) {
        //TODO (Martin): this might be obsolete because we're receiving PROVIDERS_CHANGED_ACTION
        if(ContactListActivity.D)
            Toast.makeText(this,"onProviderDisabled",Toast.LENGTH_SHORT).show();
        if(provider.equals(mProvider))
        {
            mLocationManager.removeUpdates(this);
            // Creating a criteria object to retrieve provider
            Criteria criteria = new Criteria();
            criteria.setAccuracy(Criteria.ACCURACY_FINE);
            // Getting the name of the best provider
            mProvider = mLocationManager.getBestProvider(criteria, true);

            // Getting Current Location From GPS
            if(mProvider != null)
                mLocationManager.requestLocationUpdates(mProvider, 0, 5, this);
        }
    }
}
