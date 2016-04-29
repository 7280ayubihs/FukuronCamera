package jp.co.jex.fukuroncamera;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.ImageView;

public class ImageViewActivity extends AppCompatActivity
        implements FaceDetectorAsyncTask.ProcessFinishListener {
    private static final String TAG = ImageViewActivity.class.getSimpleName();
    private static final int DEFAULT_BITMAP_HEIGHT = 720;

    private String mPathName;
    private ImageView mImageView;

    private boolean mFaceDetectorExecuted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_view);
        mImageView = (ImageView) findViewById(R.id.image_view);
        mPathName = getIntent().getStringExtra("path");
        mFaceDetectorExecuted = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!mFaceDetectorExecuted) {
            mFaceDetectorExecuted = true;

            // set bitmap
            Bitmap bitmap = resizeBitmap(BitmapFactory.decodeFile(mPathName));
            mImageView.setImageBitmap(bitmap);

            // 顔認識処理をバックグラウンドで実行
            FaceDetectorAsyncTask task = new FaceDetectorAsyncTask(this, getString(R.string.face_detector_dialog_message), bitmap);
            task.setOnProcessFinishListener(this);
            task.execute();
        }
    }

    @Override
    public void onProcessFinish(Bitmap bitmap) {
        if (bitmap != null) {
            mImageView.setImageBitmap(bitmap);
        } else {
            finish();
        }
    }

    /**
     * Bitmap をリサイズする
     */
    private Bitmap resizeBitmap(Bitmap bitmap) {
        int srcWidth = bitmap.getWidth();
        int srcHeight = bitmap.getHeight();
        if (DEFAULT_BITMAP_HEIGHT < srcHeight) {
            int dstWidth = (srcWidth * DEFAULT_BITMAP_HEIGHT) / srcHeight;
            int dstHeight = DEFAULT_BITMAP_HEIGHT;
            return Bitmap.createScaledBitmap(bitmap, dstWidth, dstHeight, false);
        } else {
            return bitmap;
        }
    }
}
