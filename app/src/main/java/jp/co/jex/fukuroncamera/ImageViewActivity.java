package jp.co.jex.fukuroncamera;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.ImageView;

public class ImageViewActivity extends AppCompatActivity
        implements FaceDetectorAsyncTask.ProcessFinishListener {

    private static final String TAG = ImageViewActivity.class.getSimpleName();

    private String mPathName;
    private ImageView mImageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_view);
        mImageView = (ImageView) findViewById(R.id.image_view);
        mPathName = getIntent().getStringExtra("path");
    }

    @Override
    protected void onResume() {
        super.onResume();

        // set bitmap
        Bitmap bitmap = BitmapFactory.decodeFile(mPathName);
        mImageView.setImageBitmap(bitmap);

        // 顔認識処理をバックグラウンドで実行
        FaceDetectorAsyncTask task = new FaceDetectorAsyncTask(this, "フクロン中...", bitmap);
        task.setOnProcessFinishListener(this);
        task.execute();
    }

    @Override
    public void onProcessFinish(Bitmap bitmap) {
        if (bitmap != null) {
            mImageView.setImageBitmap(bitmap);
        } else {
            finish();
        }
    }
}
