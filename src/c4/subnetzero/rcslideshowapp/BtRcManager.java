package c4.subnetzero.rcslideshowapp;


import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Set;
import java.util.UUID;

public class BtRcManager
{
   public static final int REQUEST_ENABLE_BT = 1;
   public static final int STATE_CHANGED = 1;
   public static final int MESSAGE_RECEIVED = 2;
   public static final int STRING_RECEIVED = 3;

   private static final String NEXUS_DEVICE_MAC = "D8:50:E6:79:7C:3B";
   private static final String LOG_TAG = "BtRcManager";
   private static final String UUID_STRING = "069c9397-7da9-4810-849c-f52f6b1deaf";
   private BluetoothAdapter mBluetoothAdapter;
   private BluetoothDevice mRemoteDevice;
   private volatile ConnectThread mConnectThread;
   private volatile ConnectedThread mConnectedThread;
   private Context mContext;
   private Handler mUiHandler;
   private Callback mCallback;
   private volatile State mState;

   public enum State
   {
      DISABLED,
      DISCONNECTED,
      CONNECTING,
      CONNECTED;
   }

   public BtRcManager(final Context context, final Handler uiHandler, final Callback callback)
   {
      mContext = context;
      mUiHandler = uiHandler;
      mCallback = callback;
      mState = State.DISABLED;

      //setup();
   }

   public synchronized void setState(final State newSate)
   {
      mState = newSate;
      mUiHandler.sendEmptyMessage(STATE_CHANGED);
   }

   public State getState()
   {
      return mState;
   }

   public boolean isBtEnabled()
   {
      return mBluetoothAdapter != null && mBluetoothAdapter.isEnabled();
   }

   public void startDiscovery()
   {
      if (mBluetoothAdapter != null && !mBluetoothAdapter.isDiscovering()) {
         mBluetoothAdapter.startDiscovery();
      }
   }

   public void stopDiscovery()
   {
      if (mBluetoothAdapter != null) {
         mBluetoothAdapter.cancelDiscovery();
      }
   }

   public void close()
   {
      if (mConnectThread != null) {
         mConnectThread.close();
      }

      if (mConnectedThread != null) {
         mConnectedThread.close();
      }

      //mContext.unregisterReceiver(mReceiver);
      stopDiscovery();
   }

   public void sendMessage(final RcMessage message)
   {
      final Gson gson = new Gson();
      Log.d(LOG_TAG, "TX: " + gson.toJson(message));
      sendString(gson.toJson(message));
   }

   public void sendString(final String msgStr)
   {
      byte[] pktData = null;
      ConnectedThread connectedThread;
      synchronized (this) {
         if (mState != State.CONNECTED) {
            return;
         }
         connectedThread = mConnectedThread;
      }

      try {
         pktData = msgStr.getBytes("UTF-8");
      } catch (UnsupportedEncodingException e) {
         /* EMPTY */
      }

      connectedThread.write(pktData);
   }

   public void startConnection()
   {
      if (mRemoteDevice == null) {
         mRemoteDevice = findRemoteDevice();
      }

      if (mRemoteDevice != null && mState == State.DISCONNECTED && mConnectThread == null) {
         mConnectThread = new ConnectThread(mRemoteDevice);
         mConnectThread.start();
      }
   }

   public void start()
   {
      //IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
      //filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
      //filter.addAction(BluetoothDevice.ACTION_UUID);

      //mContext.registerReceiver(mReceiver,filter);

      mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

      if (mBluetoothAdapter == null) {
         Toast.makeText(mContext, "Bluetooth not supported", Toast.LENGTH_SHORT);
         setState(State.DISABLED);
         return;
      }

      if (!mBluetoothAdapter.isEnabled()) {
         Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
         ((Activity) mContext).startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
         setState(State.DISABLED);
      }else {
         setState(State.DISCONNECTED);
      }
   }


   private BluetoothDevice findRemoteDevice()
   {
      BluetoothDevice remoteDevice = null;
      Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();

      if (pairedDevices.size() > 0) {
         for (BluetoothDevice device : pairedDevices) {
            if (device.getAddress().equals(NEXUS_DEVICE_MAC)) {
               remoteDevice = device;
               break;
            }
         }
      }

      return remoteDevice;
   }


   private class ConnectThread extends Thread
   {
      private static final String LOG_TAG = "ConnectThread";
      private final BluetoothSocket mmSocket;
      private final BluetoothDevice mmDevice;


      public ConnectThread(final BluetoothDevice bluetoothDevice)
      {
         BluetoothSocket tmp = null;
         mmDevice = bluetoothDevice;

         try {
            tmp = mmDevice.createRfcommSocketToServiceRecord(UUID.fromString(UUID_STRING));
         } catch (Exception e) {
            Log.e(LOG_TAG, "Unable to get connection socket: ", e);
         }
         mmSocket = tmp;
      }


      @Override
      public void run()
      {
         if (mmSocket == null) {
            mConnectThread = null;
            return;
         }

         mBluetoothAdapter.cancelDiscovery();

         try {
            setState(BtRcManager.State.CONNECTING);
            mmSocket.connect();
         } catch (Exception e) {
            Log.e(LOG_TAG, e.getMessage());
            setState(BtRcManager.State.DISCONNECTED);
            close();
            return;
         }

         setState(BtRcManager.State.CONNECTED);
         mConnectedThread = new ConnectedThread(mmSocket);
         mConnectedThread.start();
         mConnectThread = null;
      }

      public void close()
      {
         try {
            mmSocket.close();
         } catch (IOException e) {
            /* EMPTY */
         } finally {
            mConnectThread = null;
         }
      }
   }


   private class ConnectedThread extends Thread
   {
      private static final String LOG_TAG = "ConnectedThread";
      private final BluetoothSocket mmSocket;
      private final InputStream mmInStream;
      private final OutputStream mmOutStream;

      public ConnectedThread(BluetoothSocket socket)
      {
         setName(LOG_TAG);
         mmSocket = socket;
         InputStream tmpIn = null;
         OutputStream tmpOut = null;

         try {
            tmpIn = socket.getInputStream();
            tmpOut = socket.getOutputStream();
         } catch (IOException e) {
            /* EMPTY */
         }

         mmInStream = tmpIn;
         mmOutStream = tmpOut;
      }

      @Override
      public void run()
      {
         byte[] buffer = new byte[1024];
         int bytes;

         if (mmInStream == null || mmOutStream == null) {
            close();
            return;
         }

         while (true) {
            try {
               bytes = mmInStream.read(buffer);
               parsePacket(new String(buffer,0,bytes,"UTF-8"));
            } catch (IOException e) {
               Log.e(LOG_TAG,e.getMessage());
               close();
               break;
            }
         }
      }

      public void write(byte[] bytes)
      {
         if (mmSocket.isConnected()) {
            try {
               mmOutStream.write(bytes);
               mmOutStream.flush();
            } catch (IOException e) {
            /* EMPTY */
            }
         }
      }

      public void close()
      {
         try {
            mmSocket.close();
         } catch (IOException e) {
            /* EMPTY */
         } finally {
            mConnectedThread = null;
            setState(BtRcManager.State.DISCONNECTED);
         }
      }


      private void parsePacket(String msgStr)
      {
         Gson gson = new GsonBuilder().serializeNulls().create();

         try {
            msgStr = msgStr.trim();
            Log.d(LOG_TAG, "RX: " + msgStr);
            RcMessage newMsg = gson.fromJson(msgStr, RcMessage.class);
            mUiHandler.obtainMessage(MESSAGE_RECEIVED, 0, 0, newMsg).sendToTarget();
         } catch (JsonSyntaxException e) {
            mUiHandler.obtainMessage(STRING_RECEIVED, 0, 0, msgStr).sendToTarget();
         }
      }
   }


   /*
   private final BroadcastReceiver mReceiver = new BroadcastReceiver()
   {
      @Override
      public void onReceive(Context context, Intent intent)
      {
         switch (intent.getAction()) {
            case BluetoothDevice.ACTION_UUID:
               Log.d(LOG_TAG, "ACTION_UUID: " + intent.toString());
               Parcelable[] uuidExtra = intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID);
               Log.d(LOG_TAG, "ACTION_UUID: " + Arrays.toString(uuidExtra));
               break;
         }
      }
   };*/

   public interface Callback
   {

   }

}
