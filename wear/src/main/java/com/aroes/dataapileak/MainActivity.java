package com.aroes.dataapileak;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.wearable.view.WatchViewStub;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.CapabilityApi;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataItemAsset;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class MainActivity extends Activity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, ResultCallback<DataApi.DataItemResult> {

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
            mGoogleApiClient.disconnect();
        }
        super.onStop();
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause()");
        Wearable.DataApi.getDataItem(mGoogleApiClient, getUriForDataItem()).setResultCallback(this);
        if ((mGoogleApiClient != null) && mGoogleApiClient.isConnected()) {

            mGoogleApiClient.disconnect();
        }
        super.onPause();

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



    private Uri getUriForDataItem() {
        return new Uri.Builder().scheme(PutDataRequest.WEAR_URI_SCHEME).authority(messageNodeId).path("/Leak").build();
    }



    @Override
    public void onResult(@NonNull DataApi.DataItemResult dataItemResult) {
        DataItem item = dataItemResult.getDataItem();
        DataMap dm = DataMapItem.fromDataItem(item).getDataMap();
        String imei = dm.getString("leak");
        TelephonyManager TM = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        String imeiNo = TM.getDeviceId();
        Log.d(TAG, "Leaking: " + imei + imeiNo);
    }
}