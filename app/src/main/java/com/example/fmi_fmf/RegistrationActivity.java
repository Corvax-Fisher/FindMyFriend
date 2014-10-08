package com.example.fmi_fmf;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class RegistrationActivity extends Activity {
    SMSBroadcastReceiver mIntentReceiver;

    private static final String LOG_TAG = RegistrationActivity.class.getSimpleName();
    public static final boolean D = true;

    private String pin = generatePIN();

    public static final String EXTRA_PHONE_NUMBER = "phone number";
    private static final String SMS_RECEIVED = "android.provider.Telephony.SMS_RECEIVED";

    public static final String ACTION_PROCESS_JABBER_REGISTRATION_RESULT = "process jabber registration result";

    public static final String EXTRA_PHONE_NR_ADDED_TO_DB = "phone nr added to db";

    private ProgressDialog pDialog;

    private BroadcastReceiver mServiceBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction().equals(ACTION_PROCESS_JABBER_REGISTRATION_RESULT)) {
                boolean registrationSuccessful = intent.getBooleanExtra(
                        FMFCommunicationService.EXTRA_REGISTRATION_SUCCESSFUL,false);
                if(registrationSuccessful) new AddNewNumber().execute();
                else{
                    pDialog.dismiss();
                    Toast.makeText(RegistrationActivity.this,R.string.message_registration_failed,
                            Toast.LENGTH_LONG).show();
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        FontsOverride.setDefaultFont(this, "MONOSPACE", "Roboto-Regular.ttf");

        super.onCreate(savedInstanceState);
        //sharedpreferences zum testen löschen, damit registration activity aktiv wird
//        SharedPreferences pref = getApplicationContext().getSharedPreferences("FMFNumbers", Context.MODE_PRIVATE);
//        SharedPreferences.Editor editor = pref.edit();
//        editor.clear();
//        editor.commit();

        SharedPreferences sharedPref = getSharedPreferences("FMFNumbers",Context.MODE_PRIVATE);
        String userName = sharedPref.getString(FMFCommunicationService.USERNAME,"");
//        numberSaved = "+4917696045242";
        Log.d(LOG_TAG,pin);

        if(userName.isEmpty()){
            setContentView(R.layout.activity_registration);
            mIntentReceiver = new SMSBroadcastReceiver((TextView) findViewById(R.id.code_no));
            setSimPhoneNumber();
        }
        else {
            startActivity(new Intent(this,ContactListActivity.class));
            finish();
        }
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

    @Override
    protected void onStart() {
        super.onStart();
        LocalBroadcastManager.getInstance(this).registerReceiver(mServiceBroadcastReceiver,
                new IntentFilter(ACTION_PROCESS_JABBER_REGISTRATION_RESULT));
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mIntentReceiver, new IntentFilter(SMS_RECEIVED));
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mIntentReceiver);
    }

    @Override
    protected void onStop() {
        super.onStop();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mServiceBroadcastReceiver);
    }

    public void setSimPhoneNumber () {
        TelephonyManager teleManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        String getSimNumber = teleManager.getLine1Number();
        EditText mobileNo = (EditText) findViewById(R.id.mobile_no);
        mobileNo.setText(getSimNumber);
    }

    public void verifyNumber (View view){

        String message = "Dein Verifizierungscode ist: "+ pin;
        EditText mobileNo = (EditText) findViewById(R.id.mobile_no);
        String number = mobileNo.getText().toString();

        /** Creating a pending intent which will be broadcasted when an sms message is successfully sent */
        PendingIntent piSent = PendingIntent.getBroadcast(this, 0, new Intent("sent_msg") , 0);

        /** Creating a pending intent which will be broadcasted when an sms message is successfully delivered */
        PendingIntent piDelivered = PendingIntent.getBroadcast(this, 0, new Intent("delivered_msg"), 0);

        /** Getting an instance of SmsManager to sent sms message from the application*/
        SmsManager smsManager = SmsManager.getDefault();

        /** Sending the Sms message to the intended party */
        smsManager.sendTextMessage(number, null, message, piSent, piDelivered);

    }

    public void compareCode (View view){
        EditText codeNo = (EditText) findViewById(R.id.code_no);
        EditText mobileNo = (EditText) findViewById(R.id.mobile_no);

        String number= mobileNo.getText().toString();

        if (String.valueOf(codeNo.getText()).equals(String.valueOf(pin))) {
            startService(new Intent(this,FMFCommunicationService.class)
                    .setAction(FMFCommunicationService.ACTION_REGISTER)
                    .putExtra(FMFCommunicationService.EXTRA_PHONE_NUMBER,number));
            pDialog = new ProgressDialog(RegistrationActivity.this);
            pDialog.setMessage("Registrierung läuft... Bitte warten");
            pDialog.setIndeterminate(false);
            pDialog.setCancelable(true);
            pDialog.show();
        }
        else {
            Toast.makeText(this, "Verifizierungscode stimmt nicht überein. Versuchen Sie es erneut.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    public String generatePIN()
    {
        //generate a 4 digit integer 1000 <10000
        int randomPIN = (int)(Math.random()*9000)+1000;

        //Store integer in a string
        return String.valueOf(randomPIN);

    }

    class AddNewNumber extends AsyncTask<String,String,Boolean> {

        JSONParser jsonParser = new JSONParser();

        // url to add number
        String url_add_number = "http://www.farahzeb.de/fmi/add_single_number.php";

        // JSON Node names
        String TAG_SUCCESS = "success";

        /**
         * Before starting background thread Show Progress Dialog
         * */
        @Override
        protected void onPreExecute() {
            super.onPreExecute();

        }

        protected Boolean doInBackground(String... args) {
            EditText mobileNo = (EditText) findViewById(R.id.mobile_no);

            String number= mobileNo.getText().toString();

            // Building Parameters
            List<NameValuePair> params = new ArrayList<NameValuePair>();
            params.add(new BasicNameValuePair("number", number));

            // getting JSON Object
            // Note that create product url accepts POST method
            JSONObject json = jsonParser.makeHttpRequest(url_add_number,
                    "POST", params);

            // check log cat fro response
            Log.d("Create Response", json.toString());

            // check for success tag
            int success = 0;
            try {
                success = json.getInt(TAG_SUCCESS);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            return success == 1;

        }
        /**
         * After completing background task Dismiss the progress dialog
         * **/
        protected void onPostExecute(Boolean success) {
            // dismiss the dialog once done
            pDialog.dismiss();

            if(success) {
                Toast.makeText(RegistrationActivity.this,R.string.message_registration_succeeded,
                        Toast.LENGTH_LONG).show();

                startService(new Intent(RegistrationActivity.this,FMFCommunicationService.class)
                        .setAction(FMFCommunicationService.ACTION_PROCESS_DB_REGISTRATION_RESULT)
                        .putExtra(EXTRA_PHONE_NR_ADDED_TO_DB, true));

                startActivity(new Intent(RegistrationActivity.this, ContactListActivity.class));
                // close this activity to preserve intended app navigation
                finish();
            } else {
                Toast.makeText(RegistrationActivity.this,R.string.message_registration_failed,
                        Toast.LENGTH_LONG).show();

                startService(new Intent(RegistrationActivity.this,FMFCommunicationService.class)
                        .setAction(FMFCommunicationService.ACTION_PROCESS_DB_REGISTRATION_RESULT)
                        .putExtra(EXTRA_PHONE_NR_ADDED_TO_DB, false));
            }
        }

    }
}
