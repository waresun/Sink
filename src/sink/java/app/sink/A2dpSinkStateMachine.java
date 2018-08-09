diff --git a/AndroidManifest.xml b/AndroidManifest.xml
index 0c71e21..1f34195 100644
--- a/AndroidManifest.xml
+++ b/AndroidManifest.xml
@@ -17,6 +17,7 @@
         android:description="@string/permdesc_bluetoothWhitelist"
         android:protectionLevel="signature" />
 
+    <uses-permission android:name="android.permission.RECORD_AUDIO"/>
     <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
     <uses-permission android:name="android.permission.ACCESS_BLUETOOTH_SHARE" />
     <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
diff --git a/res/values/config.xml b/res/values/config.xml
index c7635bb..7aca568 100644
--- a/res/values/config.xml
+++ b/res/values/config.xml
@@ -13,11 +13,11 @@
    limitations under the License.
 -->
 <resources>
-    <bool name="profile_supported_a2dp">true</bool>
-    <bool name="profile_supported_a2dp_sink">false</bool>
+    <bool name="profile_supported_a2dp">false</bool>
+    <bool name="profile_supported_a2dp_sink">true</bool>
     <bool name="profile_supported_hdp">true</bool>
     <bool name="profile_supported_hs_hfp">true</bool>
-    <bool name="profile_supported_hfpclient">false</bool>
+    <bool name="profile_supported_hfpclient">true</bool>
     <bool name="profile_supported_hid">true</bool>
     <bool name="profile_supported_opp">true</bool>
     <bool name="profile_supported_pan">true</bool>
diff --git a/src/com/android/bluetooth/a2dpsink/A2dpSinkStateMachine.java b/src/com/android/bluetooth/a2dpsink/A2dpSinkStateMachine.java
index 07acd6f..70df37b 100644
--- a/src/com/android/bluetooth/a2dpsink/A2dpSinkStateMachine.java
+++ b/src/com/android/bluetooth/a2dpsink/A2dpSinkStateMachine.java
@@ -62,8 +62,20 @@ import java.util.ArrayList;
 import java.util.List;
 import java.util.HashMap;
 import java.util.Set;
+import android.media.AudioFormat;
+import android.media.AudioRecord;
+import android.media.AudioTrack;
+import android.media.MediaRecorder.AudioSource;
+import android.media.MediaRecorder;
+import android.media.AudioManager;
 
 final class A2dpSinkStateMachine extends StateMachine {
+    private AudioRecord recorder;
+    private AudioTrack player;
+    private int recorder_buf_size;
+    private int player_buf_size;
+    private boolean mThreadExitFlag = false;
+    private boolean isPlaying = false;
     private static final boolean DBG = true;
 
     static final int CONNECT = 1;
@@ -154,6 +166,65 @@ final class A2dpSinkStateMachine extends StateMachine {
         PowerManager pm = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
 
         mIntentBroadcastHandler = new IntentBroadcastHandler();
+        recorder_buf_size = AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
+        player_buf_size = AudioTrack.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
+    }
+    private void cleanAudioTrack()
+    {
+        audioPause();
+        mThreadExitFlag = true;
+        if (recorder != null) {
+            recorder.release();
+            recorder = null;
+        }
+        if (player != null) {
+            player.release();
+            player = null;
+        }
+    }
+    private void initAudioTrack()
+    {
+        if (recorder == null) {
+        Log.d("A2dpSinkStateMachine", "audioPlay " + AudioSource.BLUETOOTH_A2DP + " " + MediaRecorder.getAudioSourceMax() + " " + MediaRecorder.AudioSource.DEFAULT);
+            recorder = new AudioRecord(AudioSource.MIC,
+            //recorder = new AudioRecord(AudioSource.BLUETOOTH_A2DP,
+                44100,
+                AudioFormat.CHANNEL_IN_STEREO,
+                AudioFormat.ENCODING_PCM_16BIT,
+                recorder_buf_size
+                );
+        }
+
+        if (player == null) {
+            player = new AudioTrack(AudioManager.STREAM_MUSIC,
+                44100,
+                AudioFormat.CHANNEL_OUT_STEREO,
+                AudioFormat.ENCODING_PCM_16BIT,
+                player_buf_size,
+                AudioTrack.MODE_STREAM
+                );
+        }
+    }
+    private void audioPlay()
+    {
+        Log.d("A2dpSinkStateMachine", "audioPlay");
+        initAudioTrack();
+        if (isPlaying == false) {
+            isPlaying = true;
+            mThreadExitFlag = false;
+            new RecordThread().start();
+        }
+    }
+    private void audioPause()
+    {
+        if (isPlaying == true) {
+            isPlaying = false;
+            mThreadExitFlag = true;
+            recorder.stop();
+            recorder.release();
+            recorder = null;
+            player.stop();
+        }
     }
 
     static A2dpSinkStateMachine make(A2dpSinkService svc, Context context) {
@@ -183,6 +254,9 @@ final class A2dpSinkStateMachine extends StateMachine {
         @Override
         public void enter() {
             log("Enter Disconnected: " + getCurrentMessage().what);
+            if (isA2dpSinkEnabled()) {
+                cleanAudioTrack();
+            }
         }
 
         @Override
@@ -624,10 +698,16 @@ final class A2dpSinkStateMachine extends StateMachine {
             log(" processAudioStateEvent in state " + state);
             switch (state) {
                 case AUDIO_STATE_STARTED:
+                    if (isA2dpSinkEnabled()) {
+                        audioPlay();
+                    }
                     mStreaming.sendMessage(A2dpSinkStreamingStateMachine.SRC_STR_START);
                     break;
                 case AUDIO_STATE_REMOTE_SUSPEND:
                 case AUDIO_STATE_STOPPED:
+                    if (isA2dpSinkEnabled()) {
+                        audioPause();
+                    }
                     mStreaming.sendMessage(A2dpSinkStreamingStateMachine.SRC_STR_STOP);
                     break;
                 default:
@@ -848,7 +928,37 @@ final class A2dpSinkStateMachine extends StateMachine {
                 log("passthru command not sent, connection unavailable");
                 return false;
             }
+    }
+    private static boolean isA2dpSinkEnabled() {
+        ParcelUuid[] uuids = BluetoothAdapter.getDefaultAdapter().getUuids();
+        return BluetoothUuid.isUuidPresent(uuids,BluetoothUuid.AudioSink);
+    }
+    class RecordThread  extends Thread{
+        @Override
+        public void run() {
+            byte[] buffer = new byte[recorder_buf_size];
+            recorder.startRecording();
+            player.play();
+            while(true) {
+                if (mThreadExitFlag == true) {
+                    break;
+                }
+                try {
+                    int res = recorder.read(buffer, 0, recorder_buf_size);
+                    if (res>0) {
+        Log.d("A2dpSinkStateMachine", "do audioPlay");
+                        byte[] tmpBuf = new byte[res];
+                        System.arraycopy(buffer, 0, tmpBuf, 0, res);
+                        player.write(tmpBuf, 0, tmpBuf.length);
+                    }
+
+                } catch (Exception e) {
+                    e.printStackTrace();
+                    break;
+                }
+            }
         }
+    }
 
     // Event types for STACK_EVENT message
     final private static int EVENT_TYPE_NONE = 0;
