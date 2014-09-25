package com.example.fmi_fmf;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;


public class RegistrationActivity extends Activity {
    private static final String LOG_TAG = RegistrationActivity.class.getSimpleName();
    public static final boolean D = true;

    private String pin = generatePIN();

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registration);
        getMyPhoneNumber();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.registration, menu);
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


    protected void onResume() {
        super.onStart();
        if (D) Log.d(LOG_TAG, "onStart");

    }

    public void getMyPhoneNumber () {
        TelephonyManager teleManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        String getSimNumber = teleManager.getLine1Number();
        EditText mobileNo = (EditText) findViewById(R.id.mobile_no);
        mobileNo.setText(getSimNumber);
    }

    public void verifyNumber (View view){

        String message = "Follow My Friend! Das ist Dein Verifizierungscode: "+ pin;
        EditText mobileNo = (EditText) findViewById(R.id.mobile_no);
        String number = String.valueOf(mobileNo.getText());

    /** Creating a pending intent which will be broadcasted when an sms message is successfully sent */
            PendingIntent piSent = PendingIntent.getBroadcast(getBaseContext(), 0, new Intent("sent_msg") , 0);

    /** Creating a pending intent which will be broadcasted when an sms message is successfully delivered */
            PendingIntent piDelivered = PendingIntent.getBroadcast(getBaseContext(), 0, new Intent("delivered_msg"), 0);

    /** Getting an instance of SmsManager to sent sms message from the application*/
            SmsManager smsManager = SmsManager.getDefault();

    /** Sending the Sms message to the intended party */
            smsManager.sendTextMessage(number, null, message, piSent, piDelivered);
    }

    public void compareNumber (View view){
        EditText codeNo = (EditText) findViewById(R.id.code_no);

        if (String.valueOf(codeNo.getText()).equals(String.valueOf(pin))) {
            Log.d("Code farah","true");
            startService(new Intent(this,FMFCommunicationService.class));
        }
        else {
            Log.d("Code farah","false");
        }
    }

    public String generatePIN()
    {
        //generate a 4 digit integer 1000 <10000
        int randomPIN = (int)(Math.random()*9000)+1000;

        //Store integer in a string
        return String.valueOf(randomPIN);

    }
}
