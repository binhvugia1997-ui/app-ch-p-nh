package com.aiphotographer.audit;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.core.logging.TasksStatsLoggerFactory;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Run only against the verified unmodified source AARs, never against a fake logger. */
@RunWith(AndroidJUnit4.class)
public class UpstreamRuntimeTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Test public void upstreamFactoryUsesItsOwnDummyLogger() {
        assertEquals("TasksStatsDummyLogger",
            TasksStatsLoggerFactory.create(context, "PoseLandmarker", "LIVE_STREAM").getClass().getSimpleName());
    }

    @Test public void officialLocalFixturesProducePoseAndFaceLandmarks() throws Exception {
        // Fixtures stay in ignored local probe assets; never packaged in the application.
        try (PoseLandmarker pose = PoseLandmarker.createFromOptions(context,
                PoseLandmarker.PoseLandmarkerOptions.builder().setNumPoses(1)
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath("models/pose_landmarker_full.task").build()).build());
             FaceLandmarker face = FaceLandmarker.createFromOptions(context,
                FaceLandmarker.FaceLandmarkerOptions.builder().setNumFaces(1)
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath("models/face_landmarker.task").build())
                    .setOutputFaceBlendshapes(false).setOutputFacialTransformationMatrixes(true).build());
             java.io.InputStream poseFile = context.getAssets().open("pose.jpg");
             java.io.InputStream faceFile = context.getAssets().open("portrait.jpg");
             MPImage poseImage = new BitmapImageBuilder(BitmapFactory.decodeStream(poseFile)).build();
             MPImage faceImage = new BitmapImageBuilder(BitmapFactory.decodeStream(faceFile)).build()) {
            assertEquals(33, pose.detect(poseImage).landmarks().get(0).size());
            com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult result = face.detect(faceImage);
            assertEquals(478, result.faceLandmarks().get(0).size());
            assertEquals(16, result.facialTransformationMatrixes().get().get(0).length);
            assertTrue(result.faceBlendshapes().isEmpty());
        }
    }

    @Test public void poseLiveStreamCompletesOneFrameAtATime() throws Exception {
        AtomicReference<CountDownLatch> pending = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        try (PoseLandmarker task = PoseLandmarker.createFromOptions(context,
                PoseLandmarker.PoseLandmarkerOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath("models/pose_landmarker_full.task").build())
                    .setNumPoses(1).setOutputSegmentationMasks(false).setRunningMode(RunningMode.LIVE_STREAM)
                    .setResultListener((result, image) -> pending.get().countDown())
                    .setErrorListener(failure -> { error.set(failure); pending.get().countDown(); }).build());
             MPImage image = blackImage()) {
            for (long timestamp = 1; timestamp <= 3; timestamp++) {
                CountDownLatch latch = new CountDownLatch(1);
                pending.set(latch);
                task.detectAsync(image, timestamp);
                assertTrue("Pose callback timeout", latch.await(10, TimeUnit.SECONDS));
                assertNull(error.get());
            }
        }
    }

    @Test public void faceLiveStreamCompletesWithGeometryOutputEnabled() throws Exception {
        AtomicReference<CountDownLatch> pending = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        try (FaceLandmarker task = FaceLandmarker.createFromOptions(context,
                FaceLandmarker.FaceLandmarkerOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath("models/face_landmarker.task").build())
                    .setNumFaces(1).setOutputFaceBlendshapes(false).setOutputFacialTransformationMatrixes(true)
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setResultListener((result, image) -> pending.get().countDown())
                    .setErrorListener(failure -> { error.set(failure); pending.get().countDown(); }).build());
             MPImage image = blackImage()) {
            for (long timestamp = 1; timestamp <= 3; timestamp++) {
                CountDownLatch latch = new CountDownLatch(1);
                pending.set(latch);
                task.detectAsync(image, timestamp);
                assertTrue("Face callback timeout", latch.await(10, TimeUnit.SECONDS));
                assertNull(error.get());
            }
        }
    }

    private MPImage blackImage() {
        return new BitmapImageBuilder(Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)).build();
    }
}
