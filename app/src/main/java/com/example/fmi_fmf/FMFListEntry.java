package com.example.fmi_fmf;

/**
 * Created by Martin on 25.09.2014.
 */
public class FMFListEntry {
    public static boolean ONLINE = true;
    public static boolean OFFLINE = false;

    String jabberID;
    String realName;
    boolean status;

    FMFListEntry(){
        jabberID = "";
        realName = "";
        status = OFFLINE;
    }

    FMFListEntry(String jabberID, boolean status){
        this.jabberID = jabberID;
        this.status = status;
    }
}