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
public class DeleteAccountDialogFragment extends DialogFragment {

    @Override
    public void onCancel(DialogInterface dialog) {
        super.onCancel(dialog);
        Log.d(DeleteAccountDialogFragment.class.getSimpleName(), "Dialog canceled.");
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Use the Builder class for convenient dialog construction
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.title_delete_account)
                .setMessage(R.string.message_delete_account)
                .setCancelable(false)
                .setPositiveButton(R.string.accept, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        getActivity().startService(
                                new Intent(getActivity(),FMFCommunicationService.class)
                                .setAction(FMFCommunicationService.ACTION_UNREGISTER));
                    }
                })
                .setNegativeButton(R.string.decline, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                   }
                });
        // Create the AlertDialog object and return it
        return builder.create();
    }
}