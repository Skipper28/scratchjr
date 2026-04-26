package org.scratchjr.android;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;

public class CameraView extends FrameLayout {
    private static final String LOG_TAG = "ScratchJr.CameraView";

    public interface PictureCallback {
        void onPictureTaken(byte[] jpegData);
    }

    private final RectF _rect;
    private float _scale;
    private boolean _currentFacingFront;
    
    private PreviewView _previewView;
    private ImageCapture _imageCapture;
    private ProcessCameraProvider _cameraProvider;

    public CameraView(Context context, RectF rect, float scale, boolean facingFront) {
        super(context);
        _rect = rect;
        _scale = scale;
        _currentFacingFront = facingFront;

        _previewView = new PreviewView(context);
        _previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        addView(_previewView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        startCamera();
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(getContext());
        cameraProviderFuture.addListener(() -> {
            try {
                _cameraProvider = cameraProviderFuture.get();
                bindPreview();
            } catch (ExecutionException | InterruptedException e) {
                Log.e(LOG_TAG, "Error starting camera", e);
            }
        }, ContextCompat.getMainExecutor(getContext()));
    }

    private void bindPreview() {
        if (_cameraProvider == null) return;
        _cameraProvider.unbindAll();

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(_previewView.getSurfaceProvider());

        _imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(_currentFacingFront ? CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK)
                .build();

        try {
            _cameraProvider.bindToLifecycle(
                    (LifecycleOwner) getContext(),
                    cameraSelector,
                    preview,
                    _imageCapture
            );
        } catch (Exception e) {
            Log.e(LOG_TAG, "Use case binding failed", e);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return false;
    }

    public void captureStillImage(final PictureCallback pictureCallback, final Runnable failed) {
        if (_imageCapture == null) {
            failed.run();
            return;
        }

        _imageCapture.takePicture(ContextCompat.getMainExecutor(getContext()),
                new ImageCapture.OnImageCapturedCallback() {
                    @Override
                    public void onCaptureSuccess(@NonNull ImageProxy image) {
                        try {
                            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                            byte[] bytes = new byte[buffer.remaining()];
                            buffer.get(bytes);
                            
                            pictureCallback.onPictureTaken(bytes);
                        } finally {
                            image.close();
                        }
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.e(LOG_TAG, "Capture failed: " + exception.getMessage(), exception);
                        failed.run();
                    }
                });
    }

    public boolean setCameraFacing(boolean facingFront) {
        if (_currentFacingFront != facingFront) {
            _currentFacingFront = facingFront;
            post(new Runnable() {
                @Override
                public void run() {
                    bindPreview();
                }
            });
        }
        return true;
    }

    public byte[] getTransformedImage(Bitmap originalImage, int exifRotation) {
        Bitmap cropped = cropResizeAndRotate(originalImage, exifRotation);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        cropped.compress(CompressFormat.JPEG, 90, bos);
        try { bos.close(); } catch (IOException e) {}
        return bos.toByteArray();
    }

    private Bitmap cropResizeAndRotate(Bitmap image, int exifRotation) {
        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();
        float rectWidth = _rect.width();
        float rectHeight = _rect.height();

        float newHeight = rectWidth * imageHeight / imageWidth;
        float scale = rectWidth / imageWidth;
        int offsetX = 0;
        int offsetY = (int) ((newHeight - rectHeight) / 2 * imageHeight / newHeight);
        if (newHeight < rectHeight) {
            float newWidth = rectHeight * imageWidth / imageHeight;
            scale = rectHeight / imageHeight;
            offsetY = 0;
            offsetX = (int) ((newWidth - rectWidth) / 2 * imageWidth / newWidth);
        }

        Matrix m = new Matrix();
        m.postRotate(-1.0f * exifRotation);
        if (_currentFacingFront) {
            m.preScale(-1.0f, 1.0f);
        }
        m.postScale(scale / _scale, scale / _scale);
        
        return Bitmap.createBitmap(image, offsetX, offsetY, imageWidth - offsetX * 2, imageHeight - offsetY * 2, m, true);
    }
}
