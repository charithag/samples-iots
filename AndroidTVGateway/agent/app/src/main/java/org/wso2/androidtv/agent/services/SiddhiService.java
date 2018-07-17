package org.wso2.androidtv.agent.services;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import org.wso2.androidtv.agent.MessageActivity;
import org.wso2.androidtv.agent.constants.TVConstants;
import org.wso2.androidtv.agent.siddhiSources.TextEdgeSource;
import org.wso2.siddhi.core.SiddhiAppRuntime;
import org.wso2.siddhi.core.SiddhiManager;
import org.wso2.siddhi.core.event.Event;
import org.wso2.siddhi.core.stream.output.StreamCallback;

public class SiddhiService extends Service {

    private static final String TAG = SiddhiService.class.getSimpleName();

    private SiddhiManager siddhiManager;
    private SiddhiAppRuntime siddhiAppRuntime;
    private IBinder binder = new SiddhiBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Starting service: " + startId);
        String executionPlan = "";
        if (intent.hasExtra(TVConstants.EXECUTION_PLAN_EXTRA)) {
            executionPlan = intent.getExtras().getString(TVConstants.EXECUTION_PLAN_EXTRA);
        }
        deployExecutionPlan(executionPlan);
        return Service.START_STICKY;
    }

    private void deployExecutionPlan(String executionPlan) {
        cleanUpCurrentRuntime();

        if (siddhiManager == null) {
            siddhiManager = new SiddhiManager();
            siddhiManager.setExtension("source:textEdge", TextEdgeSource.class);
        }

        siddhiAppRuntime = siddhiManager.createSiddhiAppRuntime(executionPlan);
        siddhiAppRuntime.start();

        //method to show alert messages
        siddhiAppRuntime.addCallback("alertStream", new StreamCallback() {
            @Override
            public void receive(Event[] events) {
                Log.i(TAG, "alertEvent :" + events[0].getData(0));
                String alertMsg = events[0].getData(0).toString();
                showAlert(MessageActivity.class, alertMsg);
            }
        });
        Log.i(TAG, "Started execution plan.");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        cleanUpCurrentRuntime();
        if (siddhiManager != null) {
            siddhiManager.shutdown();
            siddhiManager = null;
            Log.i(TAG, "Shutting down siddhi manager.");
        }
    }

    private void cleanUpCurrentRuntime() {
        if (siddhiAppRuntime != null) {
            siddhiAppRuntime.shutdown();
            siddhiAppRuntime = null;
            Log.i(TAG, "Shutting down siddhi app runtime.");
        }
    }

    class SiddhiBinder extends Binder {
        SiddhiService getService() {
            return SiddhiService.this;
        }
    }

    private void showAlert(Class<?> cls, String extra) {
        Intent intent = new Intent(this, cls);
        intent.putExtra(TVConstants.MESSAGE, extra);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }
}
