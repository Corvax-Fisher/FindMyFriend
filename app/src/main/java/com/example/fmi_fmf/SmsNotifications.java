package com.example.fmi_fmf;

/**
 * Created by Farah on 25.09.2014.
 */

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

/**
 * This class handles the SMS sent and sms delivery broadcast intents
 */
public class SmsNotifications extends BroadcastReceiver {

    /**
     * This method will be invoked when the sms sent or sms delivery broadcast intent is received
     */
    @Override
    public void onReceive(Context context, Intent intent) {

        /**
         * Getting the intent action name to identify the broadcast intent ( whether sms sent or sms delivery )
         */
        String actionName = intent.getAction();


        if(actionName.equals("sent_msg")){
            switch(getResultCode()){
                case Activity.RESULT_OK:
                    Toast.makeText(context, "Nachricht wurde erfolgreich gesendet.", Toast.LENGTH_SHORT).show();
                    break;
                default:
                    Toast.makeText(context, "Fehler während des Sendens. Versuchen Sie es erneut.", Toast.LENGTH_SHORT).show();
                    break;
            }
        }

        if(actionName.equals("delivered_msg")){
            switch(getResultCode()){
                case Activity.RESULT_OK:
                    Toast.makeText(context, "Nachricht wurde zugestellt." , Toast.LENGTH_SHORT).show();
                    break;
                default:
                    Toast.makeText(context, "Fehler während der Zustellung. Versuchen Sie es erneut.", Toast.LENGTH_SHORT).show();
                    break;

            }

        }

    }
}
