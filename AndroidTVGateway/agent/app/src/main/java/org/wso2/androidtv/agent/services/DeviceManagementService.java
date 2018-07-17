/*
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.androidtv.agent.services;

import android.app.AlarmManager;
import android.app.DownloadManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.Nullable;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.wso2.androidtv.agent.DeviceStartUpReceiver;
import org.wso2.androidtv.agent.MessageActivity;
import org.wso2.androidtv.agent.R;
import org.wso2.androidtv.agent.VideoActivity;
import org.wso2.androidtv.agent.constants.TVConstants;
import org.wso2.androidtv.agent.h2cache.H2Connection;
import org.wso2.androidtv.agent.mqtt.AndroidTVMQTTHandler;
import org.wso2.androidtv.agent.mqtt.MessageReceivedCallback;
import org.wso2.androidtv.agent.subscribers.EdgeSourceSubscriber;
import org.wso2.androidtv.agent.util.LocalRegistry;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.net.URLDecoder;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class DeviceManagementService extends Service {

    private static final String TAG = UsbService.class.getSimpleName();

    private static AndroidTVMQTTHandler androidTVMQTTHandler;
    private UsbService usbService;
    private SiddhiService siddhiService;
    private UsbServiceHandler usbServiceHandler;
    private boolean hasPendingConfigDownload = false;
    private long downloadId = -1;

    private static volatile boolean waitFlag = false;
    private static volatile boolean isSyncPaused = false;
    private static volatile boolean isSyncStopped = false;
    private static volatile boolean isInCriticalPath = false;
    private static volatile String serialOfCurrentEdgeDevice = "";
    private static volatile String incomingMessage = "";
    private static ArrayList<EdgeSourceSubscriber> sourceSubscribers = new ArrayList<>();


    private DownloadManager downloadManager;


    private final ServiceConnection usbConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            usbService = ((UsbService.UsbBinder) iBinder).getService();
            usbService.setHandler(usbServiceHandler);
            isInCriticalPath = false;
            isSyncStopped = false;
            new Thread(syncScheduler).start();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            isSyncStopped = true;
            usbService = null;
        }
    };

    private final ServiceConnection siddhiConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            siddhiService = ((SiddhiService.SiddhiBinder) iBinder).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            siddhiService = null;
        }
    };

    private Runnable syncScheduler = new Runnable() {
        @Override
        public void run() {
            while (!isSyncStopped) {
                try {
                    for (String serial : LocalRegistry.getEdgeDevices(getApplicationContext())) {
                        while ((isInCriticalPath || isSyncPaused) && !isSyncStopped) {
                            Thread.sleep(1000);
                        }
                        if (isSyncStopped) {
                            break;
                        }
                        isInCriticalPath = true;
                        Thread.sleep(1000);
                        serialOfCurrentEdgeDevice = serial;
                        String serialH = serial.substring(0, serial.length() - 8);
                        String serialL = serial.substring(serial.length() - 8);
                        sendConfigLine("+++");
                        sendConfigLine("ATDH" + serialH + "\r");
                        sendConfigLine("ATDL" + serialL + "\r");
                        sendConfigLine("ATCN\r");
                        Thread.sleep(1000);
                        usbService.write("D\r".getBytes());
                        Thread.sleep(5000);
                        isInCriticalPath = false;
                        Thread.sleep(1000);
                        if (isSyncStopped) {
                            break;
                        }
                    }
                } catch (IOException | InterruptedException e) {
                    Log.e(TAG, e.getMessage(), e);
                }
            }
        }
    };

    private BroadcastReceiver configDownloadReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            Bundle extras = intent.getExtras();
            long currentId = extras.getLong(DownloadManager.EXTRA_DOWNLOAD_ID);
            if (downloadId == currentId) {
                DownloadManager.Query q = new DownloadManager.Query();
                q.setFilterById(currentId);
                Cursor c = downloadManager.query(q);
                if (c.moveToFirst()) {
                    int status = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS));
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        unregisterReceiver(configDownloadReceiver);
                        hasPendingConfigDownload = false;
                        String downloadFileLocalUri = c.getString(c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
                        if (downloadFileLocalUri != null) {
                            File mFile = new File(Uri.parse(downloadFileLocalUri).getPath());
                            String downloadFilePath = mFile.getAbsolutePath();
                            installConfigurations(downloadFilePath);
                        }
                    }
                }
                c.close();
            }
        }
    };


    private void installConfigurations(final String fileName) {
        Runnable installConfigThread = new Runnable() {
            @Override
            public void run() {
                if (usbService != null) {
                    isSyncPaused = true;
                    while (isInCriticalPath) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ignored) {
                        }
                    }
                    isInCriticalPath = true;
                    try {
                        sendConfigLine("+++");
                        File fXmlFile = new File(fileName);
                        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
                        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
                        Document doc = dBuilder.parse(fXmlFile);
                        doc.getDocumentElement().normalize();

                        NodeList nList = doc.getElementsByTagName("setting");
                        for (int i = 0; i < nList.getLength(); i++) {
                            Node nNode = nList.item(i);
                            if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                                Element eElement = (Element) nNode;
                                String atCommand = "AT" + eElement.getAttribute("command") + eElement.getTextContent() + "\r";
                                sendConfigLine(atCommand);
                            }
                        }
                        sendConfigLine("ATWR\r");
                        sendConfigLine("ATAC\r");
                    } catch (Exception e) {
                        usbService.write("ATNR0\r".getBytes());
                        Log.e(TAG, e.getMessage(), e);
                    }
                    Log.i(TAG, "Configs updated");
                    waitFlag = true;
                    usbService.write("ATCN\r".getBytes());
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ignored) {
                    }
                    isInCriticalPath = false;
                    isSyncPaused = true;
                }
            }
        };
        new Thread(installConfigThread).start();
    }

    private void sendConfigLine(String line) throws InterruptedException {
        waitFlag = true;
        int count = 0;
        usbService.write(line.getBytes());
        while (waitFlag) {
            Thread.sleep(100);
            if (count++ > 10) {
                usbService.write("+++".getBytes());
                break;
            }
        }
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return Service.START_STICKY;
    }

    @Override
    public void onCreate() {
        androidTVMQTTHandler = new AndroidTVMQTTHandler(this, new MessageReceivedCallback() {
            @Override
            public void onMessageReceived(JSONObject message) throws JSONException {
                performAction(message.getString("action"), message.getString("payload"));
            }
        });
        androidTVMQTTHandler.connect();

        H2Connection h2Connection = new H2Connection(this);
        try {
            h2Connection.initializeConnection();
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
        }

        usbServiceHandler = new UsbServiceHandler(this);
        /*Start UsbService(if it was not started before) and Bind it*/
        startService(UsbService.class, usbConnection, null);

        String executionPlan = LocalRegistry.getSiddhiExecutionPlan(this);
        Log.w(TAG, "EP " + executionPlan);
        if (executionPlan == null) {
            executionPlan = "@app:name('edgeAnalytics') \n" +
                    "\n" +
                    "@source(type='textEdge', @map(type='text', fail.on.missing.attribute = 'true' , regex.T=\"\"\"\"t\":(\\w+)\"\"\", regex.H=\"\"\"\"h\":(\\w+)\"\"\", regex.A=\"\"\"\"a\":(\\w+)\"\"\", regex.W=\"\"\"\"w\":(\\w+)\"\"\", regex.D=\"\"\"\"d\":(\\w+)\"\"\", regex.L=\"\"\"\"l\":(\\w+)\"\"\", @attributes(temperature = 'T', humidity = 'H', ac = 'A', window = 'W', door = 'D', light = 'L')))\n" +
                    "define stream edgeDeviceEventStream (ac int, window int, light int, temperature int, humidity int, door int); \n" +
                    "\n" +
                    "@source(type='textEdge',@map(type='text', fail.on.missing.attribute= 'true',regex.L='(LON)',@attributes(lightOn = 'L')))\n" +
                    "define stream lightOnStream (lightOn String);\n" +
                    "\n" +
                    "@source(type='textEdge',@map(type='text', fail.on.missing.attribute= 'true',regex.L='(LOFF)',@attributes(lightOff = 'L')))\n" +
                    "define stream lightOffStream (lightOff String);\n" +
                    "\n" +
                    "@sink(type='edgeGateway',topic='AC',@map(type='json'))\n" +
                    "define stream acOutputStream (AC int);\n" +
                    "\n" +
                    "@sink(type='edgeGateway',topic='HUMIDITY',@map(type='json'))\n" +
                    "define stream humidityOutputStream (HUMIDITY double);\n" +
                    "\n" +
                    "@sink(type='edgeGateway',topic='TEMP',@map(type='json'))\n" +
                    "define stream temperatureOutputStream (TEMP double);\n" +
                    "\n" +
                    "@sink(type='edgeGateway',topic='WINDOW',@map(type='json'))\n" +
                    "define stream windowOutputStream (WINDOW int);\n" +
                    "\n" +
                    "@sink(type='edgeGateway',topic='DOOR',@map(type='json'))\n" +
                    "define stream doorOutputStream (DOOR int);\n" +
                    "\n" +
                    "@sink(type='edgeResponse',topic='at_response',@map(type='json'))\n" +
                    "define stream lightStatusOutputStream (lightStatus String);\n" +
                    "\n" +
                    "@config(async = 'true') \n" +
                    "define stream alertStream (alertMessage String);\n" +
                    "\n" +
                    "from every ae1=edgeDeviceEventStream, ae2=edgeDeviceEventStream[ae1.ac != ac] \n" +
                    "select ae2.ac as AC insert into acIntermediateOutputStream;\n" +
                    "\n" +
                    "from acIntermediateOutputStream\n" +
                    "select * insert into acOutputStream;  \n" +
                    "\n" +
                    "from edgeDeviceEventStream#window.timeBatch(30 sec) \n" +
                    "select ac as AC output last every 30 sec insert into acOutputStream; \n" +
                    "\n" +
                    "from every we1=edgeDeviceEventStream, we2=edgeDeviceEventStream[we1.window != window] \n" +
                    "select we2.window as WINDOW insert into windowIntermediateOutputStream; \n" +
                    "\n" +
                    "from windowIntermediateOutputStream\n" +
                    "select * insert into windowOutputStream;  \n" +
                    "\n" +
                    "from edgeDeviceEventStream#window.timeBatch(30 sec) \n" +
                    "select window as WINDOW output last every 30 sec insert into windowOutputStream;   \n" +
                    "\n" +
                    "from every de1=edgeDeviceEventStream, de2=edgeDeviceEventStream[de1.door != door] \n" +
                    "select de2.door as DOOR insert into doorOutputStream; \n" +
                    "\n" +
                    "from edgeDeviceEventStream#window.timeBatch(30 sec) \n" +
                    "select door as DOOR output last every 30 sec insert into doorOutputStream;\n" +
                    "\n" +
                    "from edgeDeviceEventStream#window.timeBatch(30 sec) \n" +
                    "select avg(humidity) as HUMIDITY insert into humidityOutputStream;  \n" +
                    "\n" +
                    "from edgeDeviceEventStream#window.timeBatch(30 sec) \n" +
                    "select avg(temperature) as TEMP insert into temperatureOutputStream;   \n" +
                    "\n" +
                    "from windowIntermediateOutputStream[WINDOW == 1]#window.length(1) as W join acIntermediateOutputStream[AC == 1]#window.length(1) as A on W.WINDOW == 1 and A.AC == 1\n" +
                    "select 'Please turn off AC or close the window.' as alertMessage insert into alertStream; \n" +
                    "\n" +
                    "from lightOnStream \n" +
                    "select 'Light On' as lightStatus insert into lightStatusOutputStream;\n" +
                    "\n" +
                    "from lightOffStream \n" +
                    "select 'Light Off' as lightStatus insert into lightStatusOutputStream;\n";
        }
        Bundle extras = new Bundle();
        extras.putString(TVConstants.EXECUTION_PLAN_EXTRA, executionPlan);
        startService(SiddhiService.class, siddhiConnection, extras);

        downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
    }

    @Override
    public void onDestroy() {
        unbindService(usbConnection);
        unbindService(siddhiConnection);
        if (androidTVMQTTHandler != null && androidTVMQTTHandler.isConnected()) {
            androidTVMQTTHandler.disconnect();
        }
        androidTVMQTTHandler = null;
        if (hasPendingConfigDownload) {
            unregisterReceiver(configDownloadReceiver);
            hasPendingConfigDownload = false;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void performAction(String action, String payload) {
        switch (action) {
            case "video":
                startActivity(VideoActivity.class, payload);
                break;
            case "message":
                startActivity(MessageActivity.class, payload);
                break;
            case "config-url":
                configureXBee(payload);
                break;
            case "xbee-add":
                LocalRegistry.addEdgeDevice(getApplicationContext(), payload);
                serialOfCurrentEdgeDevice = payload;
                break;
            case "xbee-remove":
                LocalRegistry.removeEdgeDevice(getApplicationContext(), payload);
                break;
            case "xbee-command":
                sendCommandToEdgeDevice(payload);
                break;
            case "edgeQuery":
                LocalRegistry.addSiddhiExecutionPlan(getApplicationContext(), payload);
                Intent intentWithData = new Intent(getApplicationContext(), DeviceStartUpReceiver.class);
                PendingIntent pendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, intentWithData, 0);
                AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, 2000, pendingIntent);
                Intent startServiceIntent = new Intent(this, SiddhiService.class);
                getApplicationContext().stopService(startServiceIntent);
                stopSelf();
                break;
        }
    }

    private void sendCommandToEdgeDevice(final String payload) {
        if (usbService != null) {
            Runnable sendCommandThread = new Runnable() {
                @Override
                public void run() {
                    try {
                        isSyncPaused = true;
                        while (isInCriticalPath) {
                            Thread.sleep(1000);
                        }
                        isInCriticalPath = true;
                        Thread.sleep(1000);
                        JSONObject commandJSON = new JSONObject(payload);
                        String serial = commandJSON.getString("serial");
                        String command = commandJSON.getString("command") + "\r";
                        String serialH = serial.substring(0, serial.length() - 8);
                        String serialL = serial.substring(serial.length() - 8);
                        sendConfigLine("+++");
                        sendConfigLine("ATDH" + serialH + "\r");
                        sendConfigLine("ATDL" + serialL + "\r");
                        sendConfigLine("ATCN\r");
                        Thread.sleep(1000);
                        usbService.write(command.getBytes());
                        Thread.sleep(5000);
                    } catch (JSONException | InterruptedException e) {
                        Log.e(TAG, e.getClass().getSimpleName(), e);
                    } finally {
                        isInCriticalPath = false;
                        isSyncPaused = false;
                    }
                }
            };
            new Thread(sendCommandThread).start();
        }
    }

    private void startActivity(Class<?> cls, String extra) {
        Intent intent = new Intent(this, cls);
        intent.putExtra(TVConstants.MESSAGE, extra);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private synchronized void receivedXBeeData(String message) {
        incomingMessage += message;
        if (incomingMessage.endsWith("\r")) {
            message = incomingMessage;
            incomingMessage = "";
            processXBeeMessage(message.replace("\r", ""));

        }
    }

    private void processXBeeMessage(String message) {
        if (waitFlag && "OK".equals(message)) {
            waitFlag = false;
        } else {
            Log.i(TAG, "Message> " + message);

            /*the receiving message is published into the Siddhi Sources
            * via the source subscribers*/
            for (EdgeSourceSubscriber sourceSubscriber : sourceSubscribers) {
                sourceSubscriber.recieveEvent(message, null);
            }
        }

    }

    private void startService(Class<?> service, ServiceConnection serviceConnection, Bundle extras) {
        Intent startServiceIntent = new Intent(this, service);

        if (extras != null && !extras.isEmpty()) {
            Set<String> keys = extras.keySet();
            for (String key : keys) {
                String extra = extras.getString(key);
                startServiceIntent.putExtra(key, extra);
            }
        }
        startService(startServiceIntent);
        Intent bindingIntent = new Intent(this, service);
        bindService(bindingIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void configureXBee(String configUrl) {
        try {
            configUrl = URLDecoder.decode(configUrl, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, e.getMessage(), e);
        }
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(configUrl));
        request.setTitle(getResources().getString(R.string.app_name));
        request.setDescription("Downloading XBee configurations");
        request.setDestinationUri(null);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN);

        // get download service and enqueue file
        downloadId = downloadManager.enqueue(request);
        registerReceiver(configDownloadReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    // This handler will be passed to UsbService. Data received from serial port is displayed through this handler
    private static class UsbServiceHandler extends Handler {
        private final WeakReference<DeviceManagementService> mService;

        UsbServiceHandler(DeviceManagementService service) {
            mService = new WeakReference<>(service);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UsbService.MESSAGE_FROM_SERIAL_PORT:
                    String data = (String) msg.obj;
                    mService.get().receivedXBeeData(data);
                    break;
            }
        }
    }

    public static void connectToSource(EdgeSourceSubscriber sourceSubscriber) {
        sourceSubscribers.add(sourceSubscriber);
    }

    public static void disConnectToSource(EdgeSourceSubscriber sourceSubscriber) {
        sourceSubscribers.remove(sourceSubscriber);
    }

    public static AndroidTVMQTTHandler getAndroidTVMQTTHandler() {
        return androidTVMQTTHandler;
    }

    public static String getSerialOfCurrentEdgeDevice() {
        return serialOfCurrentEdgeDevice;
    }

}
