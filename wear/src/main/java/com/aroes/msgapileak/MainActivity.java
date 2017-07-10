package com.aroes.msgapileak;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.widget.TextView;

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


public class MainActivity extends Activity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, MessageApi.MessageListener, ResultCallback<MessageApi.SendMessageResult> {

    private TextView mTextView;
    private static final String TAG = "MainWearActivity";

    //Google services client
    private GoogleApiClient mGoogleApiClient;
    //Capability name
    private static final String
            MESSAGE_RECEIVE_CAPABILITY_NAME = "receive_message_phone";
    //Selected phone id
    private String messageNodeId = null;

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
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                mTextView = (TextView) stub.findViewById(R.id.text);
            }
        });
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
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    //Upon receiving message (send it back)
    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        byte[] leakBytes = messageEvent.getData();
        String leak = new String(leakBytes);
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Received leak: " + leak);
        }
        sendMessage(leakBytes);
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
}