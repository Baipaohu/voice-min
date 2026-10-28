package com.dsh.voicemin;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.InputStream;

public class SplashActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        SharedPreferences p = getSharedPreferences("voicemin", MODE_PRIVATE);
        if (p.getBoolean("splash_disabled", false)) {
            go();
            return;
        }
        File f = new File(getFilesDir(), "splash.png");
        boolean userSet = p.getBoolean("splash_user_set", false);
        if (!userSet && f.exists()) {
            f.delete();
        }
        if (userSet && f.exists()) {
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageBitmap(BitmapFactory.decodeFile(f.getAbsolutePath()));
            setContentView(iv);
        } else {
            setContentView(defaultSplash());
        }
        new Handler(Looper.getMainLooper()).postDelayed(this::go, 1800);
    }

    private LinearLayout defaultSplash() {
        float den = getResources().getDisplayMetrics().density;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.setGravity(Gravity.CENTER);

        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        try (InputStream in = getAssets().open("splash_default.png")) {
            iv.setImageBitmap(BitmapFactory.decodeStream(in));
        } catch (Exception ignored) {
        }
        root.addView(iv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView brand = new TextView(this);
        brand.setText("声记");
        brand.setTextSize(16);
        brand.setTextColor(Color.parseColor("#888888"));
        brand.setGravity(Gravity.CENTER);
        int pad = (int) (10 * den);
        brand.setPadding(0, pad, 0, (int) (24 * den));
        root.addView(brand);
        return root;
    }

    private void go() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
        overridePendingTransition(0, 0);
    }
}
