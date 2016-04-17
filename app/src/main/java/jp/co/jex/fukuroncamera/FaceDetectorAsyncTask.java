package jp.co.jex.fukuroncamera;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.PointF;
import android.graphics.RectF;
import android.media.FaceDetector;
import android.os.AsyncTask;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FaceDetectorAsyncTask extends AsyncTask<Void, Void, Bitmap> {
    private static final String TAG = FaceDetectorAsyncTask.class.getSimpleName();

    /** フクロン画像の倍率??? */
    private static final float MAGNIFICATION = 3.0f;

    private Activity mActivity;
    private ProgressDialog mProgressDialog;
    private String mMessage;
    private Bitmap mBitmap;
    private onProcessFinishListener mOnProcessFinishListener;

    public FaceDetectorAsyncTask(Activity activity, String message, Bitmap bitmap) {
        mActivity = activity;
        mMessage = message;
        mBitmap = bitmap;
    }

    public void setOnProcessFinishListener(onProcessFinishListener listener) {
        mOnProcessFinishListener = listener;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        mProgressDialog = new ProgressDialog(mActivity);
        mProgressDialog.setMessage(mMessage);
        mProgressDialog.show();
    }

    @Override
    protected Bitmap doInBackground(Void... params) {
        // bitmap を編集可能な 16bit深度としてコピー
        Bitmap baseBitmap = mBitmap.copy(Bitmap.Config.RGB_565, true);

        // 顔認識開始
        FaceDetector.Face faces[] = new FaceDetector.Face[8];
        FaceDetector detector = new FaceDetector(baseBitmap.getWidth(), baseBitmap.getHeight(), faces.length);
        detector.findFaces(baseBitmap, faces);

        // フクロン画像を読み込む
        Bitmap fukuron;
        try {
            fukuron = BitmapFactory.decodeStream(mActivity.getAssets().open("image/fukuron.png"));
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        // 検出した顔をフクロン画像に置き換える
        Canvas canvas = new Canvas(baseBitmap);
        for (FaceDetector.Face face: faces) {
            if (face != null) {
                float eyesDistance = face.eyesDistance();
                PointF midPoint = new PointF();
                face.getMidPoint(midPoint);

                RectF r = new RectF();
                r.left = midPoint.x - eyesDistance * MAGNIFICATION;
                r.top = midPoint.y - eyesDistance * MAGNIFICATION;
                r.right = midPoint.x + eyesDistance * MAGNIFICATION;
                r.bottom = midPoint.y + eyesDistance * MAGNIFICATION;

                Bitmap temp = Bitmap.createScaledBitmap(fukuron, (int) r.width(), (int) r.height(), false);
                canvas.drawBitmap(temp, r.left, r.top, null);
            }
        }
        return baseBitmap;
    }

    @Override
    protected void onPostExecute(Bitmap bitmap) {
        super.onPostExecute(bitmap);
        saveBitmap(bitmap);
        if (mProgressDialog.isShowing()) {
            mProgressDialog.dismiss();
        }
        mOnProcessFinishListener.onProcessFinish(bitmap);
    }

    /** bitmap を Pictures フォルダに保存する */
    private void saveBitmap(Bitmap bitmap) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.JAPAN);
            String fileName = sdf.format(new Date()) + ".jpg";
            File saveFile = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    fileName);
            FileOutputStream fos = new FileOutputStream(saveFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            fos.flush();
            fos.close();

            // save index
            ContentValues values = new ContentValues();
            ContentResolver contentResolver = mActivity.getContentResolver();
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.TITLE, fileName);
            values.put("_data", saveFile.getAbsolutePath());
            contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** 保存完了後のコールバック */
    public interface onProcessFinishListener {
        void onProcessFinish(Bitmap bitmap);
    }
}
