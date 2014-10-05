package com.example.fmi_fmf;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegistrationActivity extends Activity {
    private static final String LOG_TAG = RegistrationActivity.class.getSimpleName();
    public static final boolean D = true;

    private String pin = generatePIN();

    public static final String EXTRA_PHONE_NUMBER = "phone number";
    private static final String SMS_RECEIVED = "android.provider.Telephony.SMS_RECEIVED";

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        //sharedpreferences zum testen löschen, damit registration activity aktiv wird
        SharedPreferences pref = getApplicationContext().getSharedPreferences("FMFNumbers", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.clear();
        editor.commit();

        SharedPreferences sharedPref = getSharedPreferences("FMFNumbers",Context.MODE_PRIVATE);
        String numberSaved = "";//sharedPref.getString(EXTRA_PHONE_NUMBER,"");
//        numberSaved = "+4917696045242";
        Log.d(LOG_TAG,pin);

        if(numberSaved.isEmpty()){
            setContentView(R.layout.activity_registration);
            setSimPhoneNumber();
        }
        else {
            Intent i = new Intent(this,ContactListActivity.class)
                    .setAction(ContactListActivity.ACTION_SHOW_CONTACTS);
            this.startActivity(i);
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
    }

    @Override
    protected void onResume() {
        super.onResume();

    }

    @Override
    protected void onPause() {
        super.onPause();

    }

    @Override
    protected void onStop() {
        super.onStop();
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

        SMSBroadcastReceiver mIntentReceiver = new SMSBroadcastReceiver() {
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
                            TextView text = (TextView) findViewById(R.id.code_no);
                            text.setText(result);
                        }
                    }
                }
            }
        };
        getBaseContext().registerReceiver(mIntentReceiver, new IntentFilter("android.provider.Telephony.SMS_RECEIVED"));

        /** Creating a pending intent which will be broadcasted when an sms message is successfully sent */
        PendingIntent piSent = PendingIntent.getBroadcast(getBaseContext(), 0, new Intent("sent_msg") , 0);

        /** Creating a pending intent which will be broadcasted when an sms message is successfully delivered */
        PendingIntent piDelivered = PendingIntent.getBroadcast(getBaseContext(), 0, new Intent("delivered_msg"), 0);

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

            Intent i = new Intent(this,FMFCommunicationService.class)
                    .setAction(FMFCommunicationService.ACTION_REGISTER)
                    .putExtra(FMFCommunicationService.EXTRA_PHONE_NUMBER,number);
            this.startService(i);

            new AddNewNumber().execute();

        }
        else {
            Toast.makeText(getApplicationContext(), "Verifizierungscode stimmt nicht überein. Versuchen Sie es erneut.", Toast.LENGTH_SHORT).show();
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

        private ProgressDialog pDialog;

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
            pDialog = new ProgressDialog(RegistrationActivity.this);
            pDialog.setMessage("Registrierung läuft... Bitte warten");
            pDialog.setIndeterminate(false);
            pDialog.setCancelable(true);
            pDialog.show();
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

            if(success)
                Toast.makeText(RegistrationActivity.this, "Ihre Anmeldung war erfolgreich", Toast.LENGTH_LONG).show();

            Intent i = new Intent(RegistrationActivity.this,ContactListActivity.class)
                    .setAction(ContactListActivity.ACTION_SHOW_CONTACTS);
            startActivity(i);

            // closing this screen
            finish();
        }

    }
}
