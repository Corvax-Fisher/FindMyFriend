package com.example.fmi_fmf;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.util.Log;

/**
 * Created by Martin on 16.09.2014.
 */
public class PositionRequestDialogFragment extends DialogFragment {

    private static PositionRequestDialogFragment instance = null;

    public static PositionRequestDialogFragment getInstance() {
        if (instance == null) {
            instance = new PositionRequestDialogFragment();
        }
        return instance;
    }

    private String mFullName;

    public void setFullName(String fullName){
        mFullName = fullName;
    }

    /* The activity that creates an instance of this dialog fragment must
 * implement this interface in order to receive event callbacks.
 * Each method passes the DialogFragment in case the host needs to query it. */
    public interface RequestDialogListener {
        public void onCancel();
        public void onDialogPositiveClick();
        public void onDialogNegativeClick();
    }

    // Use this instance of the interface to deliver action events
    RequestDialogListener mListener;

    // Override the Fragment.onAttach() method to instantiate the NoticeDialogListener
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        // Verify that the host activity implements the callback interface
        try {
            // Instantiate the NoticeDialogListener so we can send events to the host
            mListener = (RequestDialogListener) activity;
        } catch (ClassCastException e) {
            // The activity doesn't implement the interface, throw exception
            throw new ClassCastException(activity.toString()
                    + " must implement NoticeDialogListener");
        }
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        super.onCancel(dialog);
        Log.d(PositionRequestDialogFragment.class.getSimpleName(), "Dialog canceled.");
        if(mListener != null) mListener.onCancel();
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Use the Builder class for convenient dialog construction
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setIcon(R.drawable.ic_launcher)
                .setMessage(mFullName + getText(R.string.message_position_request))
                .setPositiveButton(R.string.accept, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        if(mListener != null) mListener.onDialogPositiveClick();
                    }
                })
                .setNegativeButton(R.string.decline, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        if(mListener != null) mListener.onDialogNegativeClick();
                   }
                });
        // Create the AlertDialog object and return it
        return builder.create();
    }
}