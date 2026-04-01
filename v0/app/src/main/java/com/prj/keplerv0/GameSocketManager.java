package com.prj.keplerv0;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GameSocketManager {
    private static GameSocketManager instance;
    private static final int PORT = 8888;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    
    // CRITICAL: Separate executors for sending and receiving to prevent deadlocks
    private ExecutorService receiveExecutor = Executors.newSingleThreadExecutor();
    private ExecutorService sendExecutor = Executors.newSingleThreadExecutor();
    
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private OnMessageReceivedListener listener;

    public interface OnMessageReceivedListener {
        void onMessageReceived(String message);
    }

    public static synchronized GameSocketManager getInstance() {
        if (instance == null) instance = new GameSocketManager();
        return instance;
    }

    public void setListener(OnMessageReceivedListener listener) {
        this.listener = listener;
    }

    public void startServer() {
        receiveExecutor.execute(() -> {
            try {
                Log.d("KeplerNet", "Server: Starting ServerSocket on port " + PORT);
                ServerSocket serverSocket = new ServerSocket(PORT);
                serverSocket.setReuseAddress(true);
                Log.d("KeplerNet", "Server: Waiting for client...");
                socket = serverSocket.accept();
                Log.d("KeplerNet", "Server: Client connected: " + socket.getInetAddress());
                setupStreams();
                serverSocket.close(); // Close server socket after one connection
            } catch (Exception e) {
                Log.e("KeplerNet", "Server Error", e);
            }
        });
    }

    public void startClient(String hostAddress) {
        receiveExecutor.execute(() -> {
            try {
                Log.d("KeplerNet", "Client: Attempting to connect to " + hostAddress);
                int retries = 15;
                while (retries > 0) {
                    try {
                        socket = new Socket(hostAddress, PORT);
                        Log.d("KeplerNet", "Client: Connected to server");
                        setupStreams();
                        return;
                    } catch (Exception e) {
                        Log.d("KeplerNet", "Client: Connection attempt failed, retrying... (" + retries + ")");
                        retries--;
                        Thread.sleep(1000);
                    }
                }
                Log.e("KeplerNet", "Client: Failed to connect after all retries");
            } catch (Exception e) {
                Log.e("KeplerNet", "Client Error", e);
            }
        });
    }

    private void setupStreams() throws Exception {
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        Log.d("KeplerNet", "Streams setup complete. Sending LOBBY_READY");
        
        // Initial handshake to signal connection is alive
        sendMessage("LOBBY_READY");

        while (socket != null && !socket.isClosed()) {
            String msg = in.readLine();
            if (msg != null) {
                Log.d("KeplerNet", "Raw Message Received: " + msg);
                mainHandler.post(() -> {
                    if (listener != null) listener.onMessageReceived(msg);
                });
            }
        }
    }

    public void sendMessage(String message) {
        sendExecutor.execute(() -> {
            if (out != null) {
                Log.d("KeplerNet", "Sending Message: " + message);
                out.println(message);
            } else {
                Log.e("KeplerNet", "Cannot send message, stream null: " + message);
            }
        });
    }

    public void close() {
        sendExecutor.execute(() -> {
            try {
                if (socket != null) socket.close();
                socket = null;
                out = null;
                in = null;
                Log.d("KeplerNet", "Socket closed");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
