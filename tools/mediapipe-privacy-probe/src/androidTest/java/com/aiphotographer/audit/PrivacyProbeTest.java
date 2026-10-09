package com.aiphotographer.audit;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Isolated SDK audit, not a production module or device perception acceptance test. */
@RunWith(AndroidJUnit4.class)
public class PrivacyProbeTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Test public void permissionAndDependencies() throws Exception {
        assertEquals(PackageManager.PERMISSION_DENIED,
            context.checkSelfPermission(Manifest.permission.INTERNET));
        boolean present;
        try { Class.forName("com.google.android.datatransport.runtime.TransportRuntime"); present = true; }
        catch (ClassNotFoundException e) { present = false; }
        assertEquals(BuildConfig.TELEMETRY_EXPECTED, present);
    }

    @Test public void poseCreationAndInference() throws Exception {
        probe(() -> {
            try (PoseLandmarker task = PoseLandmarker.createFromOptions(context,
                PoseLandmarker.PoseLandmarkerOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath("models/pose_landmarker_lite.task").build())
                    .setNumPoses(1).build());
                 MPImage image = new BitmapImageBuilder(Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)).build()) {
                assertNotNull(task.detect(image));
            }
        });
    }

    @Test public void networkSocketIsDenied() throws Exception {
        try (java.net.Socket socket = new java.net.Socket()) {
            // Loopback only: exercise OS socket permission without sending to an external host.
            socket.connect(new java.net.InetSocketAddress("127.0.0.1", 9), 1000);
            fail("No-INTERNET app unexpectedly opened a socket");
        } catch (java.io.IOException denied) {
            String text = denied.toString();
            assertTrue(text, text.contains("EACCES") || text.contains("EPERM") || text.contains("Permission denied"));
            Log.i("PrivacyProbe", "SOCKET_PERMISSION_DENIED " + text);
        } catch (SecurityException denied) {
            Log.i("PrivacyProbe", "SOCKET_PERMISSION_DENIED " + denied);
        }
    }

    @Test public void faceCreationAndInference() throws Exception {
        probe(() -> {
            try (FaceLandmarker task = FaceLandmarker.createFromOptions(context,
                FaceLandmarker.FaceLandmarkerOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath("models/face_landmarker.task").build())
                    .setNumFaces(1).build());
                 MPImage image = new BitmapImageBuilder(Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)).build()) {
                assertNotNull(task.detect(image));
            }
        });
    }

    private void probe(Runnable action) throws Exception {
        if (BuildConfig.EXCLUDED) {
            try { action.run(); fail("Exclusion unexpectedly initialized Tasks; audit must be revisited"); }
            catch (NoClassDefFoundError expected) {
                assertTrue(expected.toString(), expected.toString().contains("datatransport"));
                Log.i("PrivacyProbe", "EXPECTED_EXCLUSION_FAILURE " + expected);
            }
            return;
        }
        action.run();
        if (!BuildConfig.TELEMETRY_EXPECTED) {
            Thread.sleep(500);
            assertFalse("No transport event database should be created",
                context.getDatabasePath("com.google.android.datatransport.events").exists());
            return;
        }
        // Transport scheduling/persistence is asynchronous. No INTERNET permission is granted.
        long deadline = android.os.SystemClock.elapsedRealtime() + 10000;
        long count = 0;
        while (android.os.SystemClock.elapsedRealtime() < deadline && count == 0) {
            if (context.getDatabasePath("com.google.android.datatransport.events").exists()) {
                try (SQLiteDatabase db = SQLiteDatabase.openDatabase(
                        context.getDatabasePath("com.google.android.datatransport.events").getPath(), null,
                        SQLiteDatabase.OPEN_READONLY);
                     Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM events", null)) {
                    if (cursor.moveToFirst()) count = cursor.getLong(0);
                }
            }
            if (count == 0) Thread.sleep(100);
        }
        Log.i("PrivacyProbe", "LOCAL_TRANSPORT_EVENT_COUNT " + count);
        assertTrue("Expected local queued telemetry despite missing INTERNET", count > 0);
    }
}
