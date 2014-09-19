package com.example.fmi_fmf;


import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;

import org.jivesoftware.smack.RosterListener;
import org.jivesoftware.smack.packet.Presence;

import java.util.Collection;

/**
 * Created by Martin on 01.09.2014.
 */
public class FMFRosterListener implements RosterListener {

    //TODO (Martin): get the ContactListView

    @Override
    public void entriesAdded(Collection<String> strings) {
        //Nothing to do
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
        //TODO (Martin): update the Presence in the ListView
    }
}
