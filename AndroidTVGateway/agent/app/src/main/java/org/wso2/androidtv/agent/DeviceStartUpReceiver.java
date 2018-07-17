package org.wso2.androidtv.agent;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.wso2.androidtv.agent.services.DeviceManagementService;

public class DeviceStartUpReceiver extends BroadcastReceiver {
    public DeviceStartUpReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Intent serviceIntent = new Intent(context, DeviceManagementService.class);
        context.startService(serviceIntent);
    }
}
