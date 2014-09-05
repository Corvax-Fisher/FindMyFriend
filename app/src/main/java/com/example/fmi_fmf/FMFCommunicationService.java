package com.example.fmi_fmf;

import android.app.ProgressDialog;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.LocationManager;
import android.os.IBinder;
import android.util.Log;

import org.jivesoftware.smack.ConnectionConfiguration;
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
import java.util.Collection;

public class FMFCommunicationService extends Service {

    private static final String LOG_TAG = "FMF_Communication_Service";

    private LocationManager mLocationManager;
    private XMPPConnection mConnection;

    public FMFCommunicationService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        SharedPreferences sharedPref = getSharedPreferences("credentials",Context.MODE_PRIVATE);
        String username = sharedPref.getString("username", "");
        String password = sharedPref.getString("password", "");
        if(!username.isEmpty() && !password.isEmpty())
        {
            connect(username, password);
        } else {
            //TODO: create register-activity
            //startActivity(new Intent(getBaseContext(),RegisterActivity.class));
        }
        mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        //Add LocationListener
        return START_STICKY; //super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        //disconnect
        //remove Location Service
    }

    public void setConnection(XMPPConnection connection) {
        this.mConnection = connection;
        if (connection != null) {
            // Add a packet listener to get messages sent to us
            PacketFilter filter = new MessageTypeFilter(Message.Type.chat);
            connection.addPacketListener(new PacketListener() {
                @Override
                public void processPacket(Packet packet) {
                    Message message = (Message) packet;
                    if (message.getBody() != null) {
                        if(message.getBody().equals("P")) {
                            //Position request
                            //TODO: check if requester is in contact list
                            // if requester is not in contact list, reply with "N"
                            // else reply with location and put requester into an ArrayList<String>
                            //   to automatically send location updates to this requester
                            //   (the requester will be removed when his status doesn't contain ":M" anymore)
                        } else if(message.getBody().equals("N")) {
                            //Position request rejected
                            //TODO: Let a Dialog Pop up that says:
                            // %Name hat sie nicht in der Kontaktliste. Standort kann nicht �bermittelt werden.
                        } else if(message.getBody().equals("S")) {
                            //Stop sending location updates to sender by removing him from the ArrayList
                        } else {
                            // Lat Lon coordinates should be received
                            //TODO: update google map route
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

    public void connect(final String username, final String password) {

        String[] userAndHost = username.split("@");
        if(userAndHost.length != 2)
        {
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

                    // Set the status to available
                    Presence presence = new Presence(Presence.Type.available);
                    presence.setMode(Presence.Mode.available);
                    presence.setStatus("Online");
//					connection.sendPacket(presence);

                    setConnection(connection);

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
}
