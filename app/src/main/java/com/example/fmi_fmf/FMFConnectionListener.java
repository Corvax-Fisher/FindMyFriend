package com.example.fmi_fmf;

import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.XMPPConnection;

/**
 * Created by Martin on 11.10.2014.
 */
public class FMFConnectionListener implements ConnectionListener {
    @Override
    public void connected(XMPPConnection xmppConnection) {
        //Not needed
    }

    @Override
    public void authenticated(XMPPConnection xmppConnection) {
        //Not needed

    }

    @Override
    public void connectionClosed() {
        //Not needed

    }

    @Override
    public void connectionClosedOnError(Exception e) {
        //Not needed

    }

    @Override
    public void reconnectingIn(int i) {
        //Not needed

    }

    @Override
    public void reconnectionSuccessful() {
        //Will be implemented when instantiating it in the Service

    }

    @Override
    public void reconnectionFailed(Exception e) {
        //Not needed

    }
}
