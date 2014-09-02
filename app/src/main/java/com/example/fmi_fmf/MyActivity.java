package com.example.fmi_fmf;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.google.android.gms.maps.GoogleMap;

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


public class MyActivity extends Activity {

    private static final String THIS_ACTIVITY = "FMF_Main_Activity";

    private com.google.android.gms.maps.MapFragment mapFragment;
    private GoogleMap googleMap;
    private XMPPConnection connection;

    private LocationManager locationManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my);

        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        String username = sharedPref.getString("username", "");
        if(!username.isEmpty())
        {
            String password = sharedPref.getString("password", "");
            connect(username, password);
            //TODO: create contact activity
            //setContentView(R.layout.activity_contacts);
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            //Add LocationListener
        } else {
            //TODO: create register/login-activity
            //setContentView(R.layout.activity_register-login);
        }

        mapFragment = (com.google.android.gms.maps.MapFragment) getFragmentManager().findFragmentById(R.id.map);
        googleMap = mapFragment.getMap();
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

    public void setConnection(XMPPConnection connection) {
        this.connection = connection;
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
                        Log.i(THIS_ACTIVITY, "Text Recieved " + message.getBody()
                                + " from " + fromName );
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
            Log.d(THIS_ACTIVITY, "Incorrect username. Cannot connect to Server.");
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
                    Log.e(THIS_ACTIVITY, "Failed to connect to "
                            + connection.getHost());
                    Log.e(THIS_ACTIVITY, ex.toString());
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
                    Log.i(THIS_ACTIVITY,
                            "Connected to " + connection.getServiceName());
                    Log.i(THIS_ACTIVITY,
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
                        Log.d(THIS_ACTIVITY,
                                "--------------------------------------");
                        Log.d(THIS_ACTIVITY, "RosterEntry " + entry);
                        Log.d(THIS_ACTIVITY,
                                "User: " + entry.getUser());
                        Log.d(THIS_ACTIVITY,
                                "Name: " + entry.getName());
                        Log.d(THIS_ACTIVITY,
                                "Status: " + entry.getStatus());
                        Log.d(THIS_ACTIVITY,
                                "Type: " + entry.getType());
                        Presence entryPresence = connection.getRoster().getPresence(entry
                                .getUser());

                        Log.d(THIS_ACTIVITY, "Presence Status: "
                                + entryPresence.getStatus());
                        Log.d(THIS_ACTIVITY, "Presence Type: "
                                + entryPresence.getType());
                        Presence.Type type = entryPresence.getType();
                        if (type == Presence.Type.available)
                            Log.d(THIS_ACTIVITY, "Presence AVIALABLE");
                        Log.d(THIS_ACTIVITY, "Presence : "
                                + entryPresence);

                    }

                } catch (XMPPException ex) {
                    Log.e(THIS_ACTIVITY, "Failed to log in as "
                            + username);
                    Log.e(THIS_ACTIVITY, ex.toString());
                    setConnection(null);
                } catch (SmackException e) {
                    // TODO Auto-generated catch block
                    Log.e(THIS_ACTIVITY, e.toString());
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
