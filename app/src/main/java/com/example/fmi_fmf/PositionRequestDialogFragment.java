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

    public static final int ID_ACCEPT = 0;
    public static final int ID_ACCEPT_AND_REQUEST = 1;
    public static final int ID_DECLINE = 2;

    /* The activity that creates an instance of this dialog fragment must
 * implement this interface in order to receive event callbacks.
 * Each method passes the DialogFragment in case the host needs to query it. */
    public interface RequestDialogListener {
        public void onClick(int which);
        public void onCancel();
//        public void onDialogPositiveClick();
//        public void onDialogNeutralClick();
//        public void onDialogNegativeClick();
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
        builder.setTitle(R.string.dialog_position_request)
                .setItems(R.array.request_choices,new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        if(mListener != null) mListener.onClick(which);
                    }
                });
        //TODO (Martin): change this to a Yes/No dialog
//                .setPositiveButton(R.string.accept, new DialogInterface.OnClickListener() {
//                    public void onClick(DialogInterface dialog, int id) {
//                        if(mListener != null) mListener.onDialogPositiveClick();
//                    }
//                })
//                .setNeutralButton(R.string.accept_and_send_request, new DialogInterface.OnClickListener() {
//                    public void onClick(DialogInterface dialog, int id) {
//                        if(mListener != null) mListener.onDialogNeutralClick();
//                   }
//                })
//                .setNegativeButton(R.string.decline, new DialogInterface.OnClickListener() {
//                    public void onClick(DialogInterface dialog, int id) {
//                        if(mListener != null) mListener.onDialogNegativeClick();
//                   }
//                });
        // Create the AlertDialog object and return it
        return builder.create();
    }
}