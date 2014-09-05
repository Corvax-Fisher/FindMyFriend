package com.example.fmi_fmf;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;


public class ContactListActivity extends Activity {

    private static final String LOG_TAG = "FMF_Main_Activity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_list);

        /* TODO (Farah):
         * - Implement a custom ListAdapter with attributes like contactName, status(, etc.?)
         * - Initialize the ListView and ListAdapter
         * - Add the ListAdapter to the ListView
         * - Implement an onItemClickListener and add it to the ListView
         */
    }

    @Override
    protected void onStart() {
        super.onStart();
        startService(new Intent(getBaseContext(), FMFCommunicationService.class));
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
}
