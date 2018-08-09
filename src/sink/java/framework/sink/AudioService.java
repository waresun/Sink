diff --git a/services/core/java/com/android/server/audio/AudioService.java b/services/core/java/com/android/server/audio/AudioService.java
index c5ecce4..3271ac1 100644
--- a/services/core/java/com/android/server/audio/AudioService.java
+++ b/services/core/java/com/android/server/audio/AudioService.java
@@ -30,6 +30,7 @@ import android.app.AppGlobals;
 import android.app.AppOpsManager;
 import android.app.NotificationManager;
 import android.bluetooth.BluetoothA2dp;
+import android.bluetooth.BluetoothA2dpSink;
 import android.bluetooth.BluetoothAdapter;
 import android.bluetooth.BluetoothClass;
 import android.bluetooth.BluetoothDevice;
@@ -680,6 +681,8 @@ public class AudioService extends IAudioService.Stub {
         IntentFilter intentFilter =
                 new IntentFilter(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED);
         intentFilter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
+        intentFilter.addAction(BluetoothA2dpSink.ACTION_CONNECTION_STATE_CHANGED);
+        intentFilter.addAction(BluetoothA2dpSink.ACTION_PLAYING_STATE_CHANGED);
         intentFilter.addAction(Intent.ACTION_DOCK_EVENT);
         intentFilter.addAction(Intent.ACTION_SCREEN_ON);
         intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
@@ -734,7 +737,7 @@ public class AudioService extends IAudioService.Stub {
         BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
         if (adapter != null) {
             adapter.getProfileProxy(mContext, mBluetoothProfileServiceListener,
-                                    BluetoothProfile.A2DP);
+                                    BluetoothProfile.A2DP_SINK);
         }
 
         mHdmiManager =
@@ -5265,7 +5268,21 @@ public class AudioService extends IAudioService.Stub {
                     AudioSystem.setForceUse(AudioSystem.FOR_DOCK, config);
                 }
                 mDockState = dockState;
-            } else if (action.equals(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)) {
+            } else if (action.equals(BluetoothA2dpSink.ACTION_CONNECTION_STATE_CHANGED)) {
+                state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE,
+                                               BluetoothProfile.STATE_DISCONNECTED);
+                BluetoothDevice btDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
+
+                onSetA2dpSourceConnectionState(btDevice, state);
+            }  else if (action.equals(BluetoothA2dpSink.ACTION_PLAYING_STATE_CHANGED)) {
+                state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE,
+                                               BluetoothA2dpSink.STATE_NOT_PLAYING);
+                BluetoothDevice btDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
+
+                if (state != BluetoothA2dpSink.STATE_NOT_PLAYING) {
+                onSetA2dpSourceConnectionState(btDevice, state);
+                }
+            }  else if (action.equals(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)) {
                 state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE,
                                                BluetoothProfile.STATE_DISCONNECTED);
                 BluetoothDevice btDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
