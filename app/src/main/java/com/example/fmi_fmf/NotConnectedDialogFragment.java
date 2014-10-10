package com.example.fmi_fmf;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.app.DialogFragment;
import android.util.Log;

/**
 * Created by Martin on 16.09.2014.
 */
public class NotConnectedDialogFragment extends DialogFragment {

    @Override
    public void onCancel(DialogInterface dialog) {
        super.onCancel(dialog);
        Log.d(NotConnectedDialogFragment.class.getSimpleName(), "Dialog canceled.");
        getActivity().finish();
//        if(mListener != null) mListener.onCancel();
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Use the Builder class for convenient dialog construction
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.title_not_connected_dialog)
                .setMessage(R.string.message_not_connected_dialog)
                .setCancelable(false)
                .setPositiveButton(R.string.accept, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
                    }
                })
                .setNegativeButton(R.string.decline, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        getActivity().finish();
                   }
                });
        // Create the AlertDialog object and return it
        return builder.create();
    }
}