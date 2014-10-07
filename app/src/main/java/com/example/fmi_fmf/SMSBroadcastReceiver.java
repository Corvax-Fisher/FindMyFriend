package com.example.fmi_fmf;

/**
 * Created by Farah on 05.10.2014.
 */

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.widget.TextView;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SMSBroadcastReceiver extends BroadcastReceiver {

    private static final String SMS_RECEIVED = "android.provider.Telephony.SMS_RECEIVED";
    private static final String TAG = "SMSBroadcastReceiver";

    TextView mCodeTextView;
    RegistrationActivity registrationActivity;
    SMSBroadcastReceiver (TextView textView){
        mCodeTextView=textView;

    }
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(SMS_RECEIVED)) {
            Bundle bundle = intent.getExtras();
            if (bundle != null) {
                Object[] pdus = (Object[])bundle.get("pdus");
                final SmsMessage[] messages = new SmsMessage[pdus.length];
                for (int i = 0; i < pdus.length; i++) {
                    messages[i] = SmsMessage.createFromPdu((byte[])pdus[i]);
                }
                if (messages.length > -1) {
                    String message = messages[0].getMessageBody();
                    Pattern intsOnly = Pattern.compile("\\d+");
                    Matcher makeMatch = intsOnly.matcher(message);
                    makeMatch.find();
                    String result = makeMatch.group();
                    mCodeTextView.setText(result);

                    if (mCodeTextView.getText().equals(result)){

                        mCodeTextView.setKeyListener(null);

                        registrationActivity.compareCode(registrationActivity.findViewById(android.R.id.content));
                    }
                }
            }
        }
    }
}
