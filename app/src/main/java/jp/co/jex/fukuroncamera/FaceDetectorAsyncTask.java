package jp.co.jex.fukuroncamera;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.PointF;
import android.graphics.RectF;
import android.media.FaceDetector;
import android.os.AsyncTask;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 *
 */
public class FaceDetectorAsyncTask extends AsyncTask<Void, Void, Bitmap> {

    private static final int EKURON = 2;
    private static final int JEXUMA = 3;

    /** android.util.Log class 用の tag */
    private static final String TAG = FaceDetectorAsyncTask.class.getSimpleName();

    /** プログレスダイアログを表示する Activity */
    private Activity mActivity;

    /** 処理完了後のコールバック */
    private ProcessFinishListener mProcessFinishListener;

    /** 処理する Bitmap */
    private Bitmap mBitmap;

    /***
     * コンストラクタ
     * @param activity プログレスダイアログを表示する Activity
     * @param bitmap 処理する Bitmap
     */
    public FaceDetectorAsyncTask(Activity activity, Bitmap bitmap) {
        mActivity = activity;
        mBitmap = bitmap;
    }

    /***
     * 処理完了後のコールバックを登録します。
     * @param listener 処理完了後のコールバック
     */
    public void setOnProcessFinishListener(ProcessFinishListener listener) {
        mProcessFinishListener = listener;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
    }

    @Override
    protected Bitmap doInBackground(Void... params) {
        // bitmap を編集可能な 16bit深度としてコピー
        Bitmap baseBitmap = mBitmap.copy(Bitmap.Config.RGB_565, true);

        // プリファレンスから倍率を読み込む
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mActivity);
        float magnification = Float.parseFloat(sp.getString(
                mActivity.getString(R.string.size_of_fukuron_key),
                mActivity.getString(R.string.size_of_fukuron_default_value)));
        int num = Integer.parseInt(sp.getString(
                mActivity.getString(R.string.number_of_fukuron_key),
                mActivity.getString(R.string.number_of_fukuron_default_value)));

        // 顔認識開始
        FaceDetector.Face faces[] = new FaceDetector.Face[num];
        FaceDetector detector = new FaceDetector(baseBitmap.getWidth(), baseBitmap.getHeight(), faces.length);
        detector.findFaces(baseBitmap, faces);

        // フクロン画像を読み込む
        Bitmap fukuron;
        Bitmap ekuron;
        Bitmap jexuma;
        try {
            fukuron = BitmapFactory.decodeStream(mActivity.getAssets().open("image/fukuron.png"));
            ekuron = BitmapFactory.decodeStream(mActivity.getAssets().open("image/ekuron.png"));
            jexuma = BitmapFactory.decodeStream(mActivity.getAssets().open("image/jexuma.png"));
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
                r.left = midPoint.x - eyesDistance * magnification;
                r.top = midPoint.y - eyesDistance * magnification;
                r.right = midPoint.x + eyesDistance * magnification;
                r.bottom = midPoint.y + eyesDistance * magnification;

                Bitmap temp;
                switch ((int) (Math.random() * 100)) {
                    case EKURON:
                        temp = Bitmap.createScaledBitmap(ekuron, (int) r.width(), (int) r.height(), false);
                        break;
                    case JEXUMA:
                        temp = Bitmap.createScaledBitmap(jexuma, (int) r.width(), (int) r.height(), false);
                        break;
                    default:
                        temp = Bitmap.createScaledBitmap(fukuron, (int) r.width(), (int) r.height(), false);
                        break;
                }
                canvas.drawBitmap(temp, r.left, r.top, null);
            }
        }
        return baseBitmap;
    }

    @Override
    protected void onPostExecute(Bitmap bitmap) {
        super.onPostExecute(bitmap);
        saveBitmap(bitmap);
        mProcessFinishListener.onProcessFinish(bitmap);
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

    /** 非同期処理が完了したさいのコールバック */
    public interface ProcessFinishListener {
        /** 非同期処理が完了した際に呼ばれます。 */
        void onProcessFinish(Bitmap bitmap);
    }
}
