package com.example.fmi_fmf;

import android.content.Context;
import android.graphics.drawable.TransitionDrawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

/**
 * Created by Martin on 28.09.2014.
 */
public class ContactListAdapter extends ArrayAdapter<FMFListEntry> {

    private HashMap<String,Boolean> mStatusChanged;
    private HashMap<String,Integer> mRosterMap;

    private boolean mReloadStatus;
    private int viewResource;

    public ContactListAdapter(Context context, int resource, int textViewResourceId, List<FMFListEntry> objects) {
        super(context, resource, textViewResourceId, objects);

        viewResource = resource;
        mStatusChanged = new HashMap<String, Boolean>(objects.size());
        mRosterMap = new HashMap<String, Integer>(objects.size());
        for(FMFListEntry listEntry : objects)
            mStatusChanged.put(listEntry.jabberID,false);

        sort();
    }

    public ContactListAdapter(Context context, int resource, int textViewResourceId) {
        super(context, resource, textViewResourceId);

        viewResource = resource;
        mStatusChanged = new HashMap<String, Boolean>();
        mRosterMap = new HashMap<String, Integer>();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        //TODO: implement this

        if(convertView == null) {
            LayoutInflater inflater = (LayoutInflater)
                    getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(viewResource,null);

            ImageView statusView = (ImageView) convertView.findViewById(R.id.statusView);
            TransitionDrawable statusTransition = (TransitionDrawable) statusView.getDrawable();

            if(mStatusChanged.get(this.getItem(position).toString()) == true)
            {
                mStatusChanged.put(this.getItem(position).toString(), false);
                if(this.getItem(position).status == FMFListEntry.ONLINE)
                    statusTransition.startTransition(500);
                else statusTransition.reverseTransition(500);
            }
        }

        else if(convertView != null) {
            ImageView statusView = (ImageView) convertView.findViewById(R.id.statusView);
            TransitionDrawable statusTransition = (TransitionDrawable) statusView.getDrawable();
            if(mReloadStatus) {
                statusTransition.resetTransition();
                if(mStatusChanged.get(this.getItem(position).toString()) == false &&
                        this.getItem(position).status == FMFListEntry.ONLINE)
                    statusTransition.startTransition(0);
                if (position == this.getCount() - 1)
                    mReloadStatus = false;
            }
            if(mStatusChanged.get(this.getItem(position).toString()) == true)
            {
                mStatusChanged.put(this.getItem(position).toString(), false);
                if(this.getItem(position).status == FMFListEntry.ONLINE)
                    statusTransition.startTransition(500);
                else statusTransition.reverseTransition(500);
            }
        }

        return super.getView(position, convertView, parent);
    }

    @Override
    public void add(FMFListEntry object) {
        super.add(object);
        mStatusChanged.put(object.toString(),false);
        sort();
        mReloadStatus = true;
        notifyDataSetChanged();
    }

    @Override
    public void addAll(Collection<? extends FMFListEntry> collection) {
        super.addAll(collection);
        for(FMFListEntry entry : collection) mStatusChanged.put(entry.toString(),false);
        sort();
        mReloadStatus = true;
        notifyDataSetChanged();
    }

    public void setStatusByJabberId(String jabberId, boolean status) {
        //TODO bugfix: jabberId includes resource, RosterMap doesn't
        if(mRosterMap.get(jabberId) != null)
        {
            if(this.getItem(mRosterMap.get(jabberId)).status != status)
            {
                mStatusChanged.put(this.getItem(mRosterMap.get(jabberId)).toString(), true);
                this.getItem(mRosterMap.get(jabberId)).status = status;
                notifyDataSetChanged();
            }
        }
    }

    public String resolveJabberIdToRealName(String jabberId) {
        if(mRosterMap.get(jabberId) != null)
            return this.getItem(mRosterMap.get(jabberId)).toString();
        else return null;
    }

    public boolean contains(String jabberId) {
        return mRosterMap.containsKey(jabberId);
    }

    private void sort(){
        sort(new Comparator<FMFListEntry>() {
            @Override
            public int compare(FMFListEntry lhs, FMFListEntry rhs) {
                return lhs.realName.compareToIgnoreCase(rhs.realName);
            }
        });

        for (int position = 0; position < this.getCount(); position++)
            mRosterMap.put(this.getItem(position).jabberID,position);
    }

//    public void setStatusByPosition(int position, boolean status) {
//        if(this.getItem(position).status != status)
//            mStatusChanged.put(this.getItem(position).toString(), true);
//        getItem(position).status = status;
//        notifyDataSetChanged();
//    }
}
