package jp.co.jex.fukuroncamera;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.widget.ImageView;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class CameraActivity extends Activity
        implements ImageReader.OnImageAvailableListener, FaceDetectorAsyncTask.ProcessFinishListener {

    /**
     * {@link Log}用のタグ
     */
    private static final String TAG = CameraActivity.class.getSimpleName();

    /**
     *
     */
    private static final int PERMISSION_REQUEST_CODE = 1001;

    /**
     *
     */
    private static final String[] PERMISSION_LIST = {
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    /**
     * Conversion from screen rotation to JPEG orientation.
     */
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    /**
     *
     */
    private static final Size MAX_PREVIEW_SIZE = new Size(1920, 1080);

    /**
     *
     */
    private static final Size PREVIEW_ASPECT_RATIO = new Size(4, 3);

    @BindView(R.id.textureView)
    com.example.android.camera2basic.AutoFitTextureView mTextureView;

    /**
     * The current state of camera state for taking pictures.
     */
    private CameraActivity.CameraState mState = CameraActivity.CameraState.STATE_PREVIEW;

    private enum CameraState {
        /**
         * Camera state: Showing camera preview.
         */
        STATE_PREVIEW,
        /**
         * Camera state: Waiting for the focus to be locked.
         */
        STATE_WAITING_LOCK,
        /**
         * Camera state: Waiting for the exposure to be precapture state.
         */
        STATE_WAITING_PRECAPTURE,
        /**
         * Camera state: Waiting for the exposure state to be something other than precapture.
         */
        STATE_WAITING_NON_PRECAPTURE,
        /**
         * Camera state: Picture was taken.
         */
        STATE_PICTURE_TAKEN,
    }

    /**
     * {@link #mTextureView} から画像を取得するインスタンス
     */
    private ImageReader mImageReader;

    /**
     *{@link #mTextureView} から画像を取得するためのコールバック
     */
    @Override
    public void onImageAvailable(ImageReader reader) {
        Image image = reader.acquireLatestImage();
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        image.close();

        // 顔認識処理をバックグラウンドで実行
        FaceDetectorAsyncTask task = new FaceDetectorAsyncTask(this, getString(R.string.face_detector_dialog_message), bitmap);
        task.setOnProcessFinishListener(this);
        task.execute();
    }

    /**
     * 顔認証処理が完了した際に実行されるコールバック
     */
    @Override
    public void onProcessFinish(Bitmap bitmap) {
        createImagePreviewDialog(bitmap).show();
    }

    private Dialog createImagePreviewDialog(Bitmap bitmap) {
        ImageView imageView = new ImageView(this);
        imageView.setImageBitmap(bitmap);
        imageView.setScaleType(ImageView.ScaleType.FIT_XY);
        imageView.setAdjustViewBounds(true);

        Dialog dialog = new Dialog(this);
        dialog.setContentView(imageView);

        return dialog;
    }

    /**
     *
     */
    private CameraManager mCameraManager;

    /**
     *
     */
    private String mCameraId;

    /**
     *
     */
    private CameraDevice mCameraDevice;

    /**
     *
     */
    private CameraCaptureSession mCameraCaptureSession;

    /**
     *
     */
    private Size mPreviewSize;

    /**
     * {@link CaptureRequest.Builder} for the camera preview
     */
    private CaptureRequest.Builder mPreviewRequestBuilder;

    /**
     * {@link CaptureRequest} generated by {@link #mPreviewRequestBuilder}
     */
    private CaptureRequest mPreviewRequest;

    /**
     * A {@link CameraCaptureSession.CaptureCallback} that handles events related to JPEG capture.
     */
    private CameraCaptureSession.CaptureCallback mCameraCaptureSessionCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
            //super.onCaptureProgressed(session, request, partialResult);
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            //super.onCaptureCompleted(session, request, result);
            process(result);
        }

        private void process(CaptureResult result) {
            switch (mState) {
                case STATE_PREVIEW: {
                    // なにもしない。
                    break;
                }
                case STATE_WAITING_LOCK: {
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (afState == null) {
                        captureStillPicture();
                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState || CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
                        // CONTROL_AE_STATE can be null on some devices
                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                        if (aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            mState = CameraActivity.CameraState.STATE_PICTURE_TAKEN;
                            captureStillPicture();
                        } else {
                            runPrecaptureSequence();
                        }
                    }
                    break;
                }
                case STATE_WAITING_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE || aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        mState = CameraActivity.CameraState.STATE_WAITING_NON_PRECAPTURE;
                    }
                    break;
                }
                case STATE_WAITING_NON_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        mState = CameraActivity.CameraState.STATE_PICTURE_TAKEN;
                        captureStillPicture();
                    }
                    break;
                }
            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        ButterKnife.bind(this);
        mCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mTextureView.isAvailable()) {
            openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                    openCamera(width, height);
                }

                @Override
                public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                    configureTransform(width, height);
                }

                @Override
                public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                    // なにもしない
                }
            });
        }
    }

    @Override
    protected void onPause() {
        closeCamera();
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    createPermissionsErrorDialog().show();
                    break;
                }
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    /**
     *
     */
    private android.support.v7.app.AlertDialog createPermissionsErrorDialog() {
        return new android.support.v7.app.AlertDialog.Builder(this)
                .setIcon(android.R.drawable.ic_dialog_info)
                .setTitle(R.string.permissions_error_dialog_title)
                .setMessage(R.string.permissions_error_dialog_message)
                .setCancelable(false)
                .setPositiveButton(R.string.permissions_error_dialog_positive_button_text, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                })
                .create();
    }

    private void openCamera(int width, int height) {
        if (Build.VERSION_CODES.M <= Build.VERSION.SDK_INT) {
            for (String permission : PERMISSION_LIST) {
                if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(PERMISSION_LIST, PERMISSION_REQUEST_CODE);
                    return;
                }
            }
        }

        chooseCameraFacingBack();
        configureTransform(width, height);
        try {
            mCameraManager.openCamera(mCameraId,
                    new CameraDevice.StateCallback() {
                        @Override
                        public void onOpened(@NonNull CameraDevice camera) {
                            mCameraDevice = camera;
                            createCaptureSession();
                        }

                        @Override
                        public void onDisconnected(@NonNull CameraDevice camera) {
                            mCameraDevice = null;
                        }

                        @Override
                        public void onError(@NonNull CameraDevice camera, int error) {
                            mCameraDevice = null;
                        }
                    }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {
        if (mCameraCaptureSession != null) {
            mCameraCaptureSession.close();
            mCameraCaptureSession = null;
        }
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        if (mImageReader != null) {
            mImageReader.close();
            mImageReader = null;
        }
    }

    private void createCaptureSession() {
        if (mTextureView == null) {
            return;
        }
        SurfaceTexture texture = mTextureView.getSurfaceTexture();
        if (texture == null) {
            return;
        }
        texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        Surface surface = new Surface(texture);
        try {
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);
            mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            if (mCameraDevice == null) {
                                return;
                            }
                            mCameraCaptureSession = session;
                            try {
                                // プレビュー時のリクエスト用インスタンスを作成後、再利用するために保持する
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                                mPreviewRequest = mPreviewRequestBuilder.build();

                                // プレビュー開始
                                mCameraCaptureSession.setRepeatingRequest(mPreviewRequest, mCameraCaptureSessionCaptureCallback, null);
                            } catch (CameraAccessException e) {
                                Log.e(TAG, "Failed to start camera preview.", e);
                            }
                        }
                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Log.e(TAG, "Failed to configure capture session.");
                        }
                    }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     *
     */
    private void chooseCameraFacingBack() {
        try {
            String[] cameraIdList = mCameraManager.getCameraIdList();
            if (cameraIdList.length == 0) {
                throw new RuntimeException("No camera available.");
            }
            for (String cameraId : cameraIdList) {
                CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(cameraId);

                // カメラの向きをチェック
                Integer lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (lensFacing == null || lensFacing != CameraCharacteristics.LENS_FACING_BACK) {
                    continue;
                }

                // プレビューサイズを取得
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }
                mPreviewSize = choosePreviewSize(map.getOutputSizes(ImageFormat.JPEG), MAX_PREVIEW_SIZE, PREVIEW_ASPECT_RATIO);

                // ImageReaderを生成
                mImageReader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(), ImageFormat.JPEG, 2);
                mImageReader.setOnImageAvailableListener(this, null);

                // mTextureViewのアスペクト比を更新
                setTextureViewAspectRatio();

                mCameraId = cameraId;
                return;
            }

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     *
     */
    private Size choosePreviewSize(Size[] sizes, Size maxSize, Size aspectRatio) {
        // sizes から 指定されたアスペクト比のものだけにする
        ArrayList<Size> sizeList = new ArrayList<>();
        for (Size size : sizes) {
            // 最大サイズを超えている場合は、対象外
            if (maxSize.getWidth() < size.getWidth() || maxSize.getHeight() < size.getHeight()) {
                continue;
            }
            // アスペクト比のチェック
            if (size.getHeight() == size.getWidth() * aspectRatio.getHeight() / aspectRatio.getWidth()) {
                sizeList.add(size);
            }
        }
        Log.d(TAG, "choosePreviewSize(): sizeList" + sizeList.toString());

        Size result = null;
        for (Size size : sizeList) {
            if (result == null) {
                result = size;
            } else {
                if (result.getWidth() < size.getWidth() && result.getHeight() < size.getHeight()) {
                    result = size;
                }
            }
        }
        Log.d(TAG, "choosePreviewSize(): maxSize => " + result.toString());
        return result;
    }

    /**
     *
     */
    private void setTextureViewAspectRatio() {
        if (mTextureView == null || mPreviewSize == null) {
            return;
        }

        int layoutWidth;
        int layoutHeight;
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            float magRatioWidth = ((float) mTextureView.getWidth()) / ((float) mPreviewSize.getHeight());
            float magRatioHeight = ((float) mTextureView.getHeight()) / ((float) mPreviewSize.getWidth());
            if (magRatioWidth <= magRatioHeight) {
                layoutWidth = mTextureView.getWidth();
                layoutHeight = (int) (mPreviewSize.getWidth() * magRatioWidth);
            } else {
                layoutWidth = (int) (mPreviewSize.getHeight() * magRatioHeight);
                layoutHeight = mTextureView.getHeight();
            }
        } else {
            float magRatioWidth = ((float) mTextureView.getWidth()) / ((float) mPreviewSize.getWidth());
            float magRatioHeight = ((float) mTextureView.getHeight()) / ((float) mPreviewSize.getHeight());
            if (magRatioWidth <= magRatioHeight) {
                layoutWidth = mTextureView.getWidth();
                layoutHeight = (int) (mPreviewSize.getHeight() * magRatioWidth);
            } else {
                layoutWidth = (int) (mPreviewSize.getWidth() * magRatioHeight);
                layoutHeight = mTextureView.getHeight();
            }
        }
        mTextureView.setAspectRatio(layoutWidth, layoutHeight);
    }

    /**
     * Configures the necessary {@link android.graphics.Matrix} transformation to `mTextureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private void configureTransform(int viewWidth, int viewHeight) {
        if (null == mTextureView || null == mPreviewSize) {
            return;
        }
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }

    /**
     * Lock the focus as the first step for a still image capture.
     */
    @OnClick(R.id.imageButtonTakePicture)
    void onClickImageButtonTakePicture() {
        try {
            // This is how to tell the camera to lock focus.
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the lock.
            mState = CameraActivity.CameraState.STATE_WAITING_LOCK;
            mCameraCaptureSession.capture(mPreviewRequestBuilder.build(), mCameraCaptureSessionCaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Run the precapture sequence for capturing a still image. This method should be called when
     * we get a response in {@link #mCameraCaptureSessionCaptureCallback} from {@link #onClickImageButtonTakePicture()}.
     */
    private void runPrecaptureSequence() {
        try {
            // This is how to tell the camera to trigger.
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the precapture sequence to be set.
            mState = CameraActivity.CameraState.STATE_WAITING_PRECAPTURE;
            mCameraCaptureSession.capture(mPreviewRequestBuilder.build(), mCameraCaptureSessionCaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Capture a still picture. This method should be called when we get a response in
     * {@link #mCameraCaptureSessionCaptureCallback} from both {@link #onClickImageButtonTakePicture()}.
     */
    private void captureStillPicture() {
        try {
            if (null == mCameraDevice) {
                return;
            }
            // This is the CaptureRequest.Builder that we use to take a picture.
            final CaptureRequest.Builder captureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader.getSurface());

            // Use the same AE and AF modes as the preview.
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

            // Orientation
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));
            CameraCaptureSession.CaptureCallback CaptureCallback = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {
                    unlockFocus();
                }
            };

            mCameraCaptureSession.stopRepeating();
            mCameraCaptureSession.capture(captureBuilder.build(), CaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Unlock the focus. This method should be called when still image capture sequence is
     * finished.
     */
    private void unlockFocus() {
        try {
            // Reset the auto-focus trigger
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            mCameraCaptureSession.capture(mPreviewRequestBuilder.build(), mCameraCaptureSessionCaptureCallback, null);
            // After this, the camera will go back to the normal state of preview.
            mState = CameraActivity.CameraState.STATE_PREVIEW;
            mCameraCaptureSession.setRepeatingRequest(mPreviewRequest, mCameraCaptureSessionCaptureCallback, null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
}
