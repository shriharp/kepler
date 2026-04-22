package com.prj.keplerv0;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class MultiplayerSetupActivity extends AppCompatActivity implements WifiP2pManager.ConnectionInfoListener {

    private static final String TAG = "KeplerMultiplayer";
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private WifiP2pManager manager;
    private WifiP2pManager.Channel channel;
    private BroadcastReceiver receiver;
    private IntentFilter intentFilter;

    private TextView tvStatus, tvTitle;
    private RecyclerView rvDevices;
    private DeviceAdapter adapter;
    private List<WifiP2pDevice> peers = new ArrayList<>();
    
    private GameSocketManager socketManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_multiplayer_setup);

        tvStatus = findViewById(R.id.tv_status);
        tvTitle = findViewById(R.id.tv_multiplayer_title);
        rvDevices = findViewById(R.id.rv_devices);
        Button btnHost = findViewById(R.id.btn_host_game);
        Button btnJoin = findViewById(R.id.btn_join_game);
        Button btnSingle = findViewById(R.id.btn_single_player);

        rvDevices.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DeviceAdapter(peers, this::connectToPeer);
        rvDevices.setAdapter(adapter);

        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);
        
        receiver = new WiFiDirectBroadcastReceiver();
        intentFilter = new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        btnHost.setOnClickListener(v -> handleAction(true));
        btnJoin.setOnClickListener(v -> handleAction(false));
        
        btnSingle.setOnClickListener(v -> {
            Intent intent = new Intent(MultiplayerSetupActivity.this, DeckSelectionActivity.class);
            intent.putExtra("is_multiplayer", false);
            startActivity(intent);
            finish();
        });
        
        socketManager = GameSocketManager.getInstance();
        socketManager.close(); // Clean up any lingering sockets from previous games
    }

    private void handleAction(boolean isHost) {
        if (!checkPermissions()) return;

        // Robust cleanup to prevent BUSY (2) errors
        manager.cancelConnect(channel, null);
        manager.stopPeerDiscovery(channel, null);

        manager.removeGroup(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                proceedWithAction(isHost);
            }

            @Override
            public void onFailure(int reason) {
                proceedWithAction(isHost);
            }
        });
    }

    private void proceedWithAction(boolean isHost) {
        // Small delay to let the framework breathe after removing group
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (isHost) startHosting();
            else startJoining();
        }, 500);
    }

    private void startHosting() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;

        manager.createGroup(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                tvStatus.setText("Status: Hosting... Waiting for client to join.");
                Toast.makeText(MultiplayerSetupActivity.this, "Hosting started.", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(int reason) {
                tvStatus.setText("Status: Host Error " + reason);
            }
        });
    }

    private void startJoining() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;

        manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                tvStatus.setText("Status: Searching for Game IDs...");
            }

            @Override
            public void onFailure(int reason) {
                tvStatus.setText("Status: Discovery Failed " + reason);
            }
        });
    }

    private boolean checkPermissions() {
        List<String> perms = new ArrayList<>();
        perms.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        }

        List<String> missing = new ArrayList<>();
        for (String p : perms) {
            if (ActivityCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                missing.add(p);
            }
        }

        if (!missing.isEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toArray(new String[0]), PERMISSION_REQUEST_CODE);
            return false;
        }
        return true;
    }

    private void connectToPeer(WifiP2pDevice device) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;

        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;
        
        tvStatus.setText("Status: Requesting connection to " + device.deviceName);
        manager.connect(channel, config, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Connection request sent successfully.");
            }

            @Override
            public void onFailure(int reason) {
                tvStatus.setText("Status: Connection request failed.");
            }
        });
    }

    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo info) {
        if (info == null || !info.groupFormed || info.groupOwnerAddress == null) {
            Log.d(TAG, "Connection info not ready yet.");
            return;
        }

        tvStatus.setText("Status: Wi-Fi Connected. Establishing Game Link...");
        
        socketManager.setListener(message -> {
            if ("LOBBY_READY".equals(message)) {
                tvStatus.setText("Status: Game Link Ready!");
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    Intent intent = new Intent(this, DeckSelectionActivity.class);
                    intent.putExtra("is_multiplayer", true);
                    intent.putExtra("is_host", info.isGroupOwner);
                    intent.putExtra("host_address", info.groupOwnerAddress.getHostAddress());
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);
                    finish();
                }, 1000);
            }
        });

        if (info.isGroupOwner) {
            socketManager.startServer();
        } else {
            socketManager.startClient(info.groupOwnerAddress.getHostAddress());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(receiver, intentFilter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
    }

    private class WiFiDirectBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
                WifiP2pDevice device = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE);
                if (device != null) {
                    tvTitle.setText("Game ID: " + device.deviceName);
                }
            } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
                if (manager != null) {
                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        manager.requestPeers(channel, peerListListener);
                    }
                }
            } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
                if (manager == null) return;
                manager.requestConnectionInfo(channel, MultiplayerSetupActivity.this);
            }
        }
    }

    private WifiP2pManager.PeerListListener peerListListener = peerList -> {
        peers.clear();
        peers.addAll(peerList.getDeviceList());
        adapter.notifyDataSetChanged();
    };

    private static class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.ViewHolder> {
        interface OnDeviceClickListener { void onDeviceClick(WifiP2pDevice device); }
        private List<WifiP2pDevice> devices;
        private OnDeviceClickListener listener;

        DeviceAdapter(List<WifiP2pDevice> devices, OnDeviceClickListener listener) {
            this.devices = devices;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_2, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            WifiP2pDevice d = devices.get(position);
            holder.text1.setText(d.deviceName);
            holder.text2.setText(d.deviceAddress);
            holder.itemView.setOnClickListener(v -> listener.onDeviceClick(d));
        }

        @Override
        public int getItemCount() { return devices.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView text1, text2;
            ViewHolder(View v) {
                super(v);
                text1 = v.findViewById(android.R.id.text1);
                text2 = v.findViewById(android.R.id.text2);
                text1.setTextColor(android.graphics.Color.WHITE);
                text2.setTextColor(android.graphics.Color.GRAY);
            }
        }
    }
}
