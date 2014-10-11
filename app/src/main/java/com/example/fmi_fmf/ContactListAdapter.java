package com.example.fmi_fmf;

import android.content.Context;
import android.graphics.drawable.TransitionDrawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

/**
 * Created by Martin on 28.09.2014.
 */
public class ContactListAdapter extends ArrayAdapter<FMFListEntry> {

    private HashMap<String,Integer> mRosterMap;

    private int viewResource;

    public ContactListAdapter(Context context, int resource, int textViewResourceId, List<FMFListEntry> objects) {
        super(context, resource, textViewResourceId, objects);

        viewResource = resource;
        mRosterMap = new HashMap<String, Integer>(objects.size());

        sort();
    }

    public ContactListAdapter(Context context, int resource, int textViewResourceId) {
        super(context, resource, textViewResourceId);

        viewResource = resource;
        mRosterMap = new HashMap<String, Integer>();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if(convertView == null) {
            LayoutInflater inflater = (LayoutInflater)
                    getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(viewResource, null);
        }
        ImageView statusView = (ImageView) convertView.findViewById(R.id.statusView);
        if(this.getItem(position).status == FMFListEntry.ONLINE)
            statusView.setImageResource(android.R.drawable.presence_online);
        else statusView.setImageResource(android.R.drawable.presence_offline);

        return super.getView(position, convertView, parent);
    }

    @Override
    public void add(FMFListEntry object) {
        super.add(object);
        sort();
        notifyDataSetChanged();
    }

    @Override
    public void addAll(Collection<? extends FMFListEntry> collection) {
        super.addAll(collection);
        sort();
        notifyDataSetChanged();
    }

    public void setStatusByJabberId(String jabberId, boolean status) {
        if(mRosterMap.get(jabberId) != null)
        {
            FMFListEntry adapterListEntry = this.getItem(mRosterMap.get(jabberId));
            if(adapterListEntry.status != status)
            {
                adapterListEntry.status = status;
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
