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
import java.util.Random;

public class SplashActivity extends Activity {
    private static final String[] QUOTES = {
            "千里之行，始于足下。",
            "不积跬步，无以至千里。",
            "天道酬勤。",
            "厚积薄发。",
            "行胜于言。",
            "与其临渊羡鱼，不如退而结网。",
            "工欲善其事，必先利其器。",
            "路虽远，行则将至；事虽难，做则必成。",
            "不怕慢，就怕站。",
            "坚持，是最朴素的天赋。",
            "所有的努力，都不会白费。",
            "越努力，越幸运。",
            "今天多学一点，明天少求人一点。",
            "你只管努力，剩下的交给时间。",
            "星光不问赶路人，时光不负有心人。",
            "你现在的努力，是为了以后有更多选择。",
            "别怕路长，只怕心老。",
            "每天进步一点点。",
            "慢慢来，比较快。",
            "心有光芒，必有远方。",
            "知行合一，方能致远。",
            "博观而约取，厚积而薄发。",
            "业精于勤，荒于嬉。",
            "锲而不舍，金石可镂。",
    };

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
            setContentView(textSplash());
        }
        new Handler(Looper.getMainLooper()).postDelayed(this::go, 1800);
    }

    private LinearLayout textSplash() {
        float den = getResources().getDisplayMetrics().density;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.parseColor("#163A6B"));
        int pad = (int) (32 * den);
        root.setPadding(pad, pad, pad, pad);

        TextView brand = new TextView(this);
        brand.setText("声 记");
        brand.setTextSize(20);
        brand.setTextColor(Color.parseColor("#B3FFFFFF"));
        root.addView(brand);

        String q = "广告位招租 [doge]";
        TextView quote = new TextView(this);
        quote.setText(q);
        quote.setTextSize(28);
        quote.setTextColor(Color.WHITE);
        quote.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (26 * den);
        root.addView(quote, lp);

        TextView hint = new TextView(this);
        hint.setText("录音 · 转写 · 提问");
        hint.setTextSize(13);
        hint.setTextColor(Color.parseColor("#80FFFFFF"));
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp2.topMargin = (int) (20 * den);
        root.addView(hint, lp2);
        return root;
    }

    private void go() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
        overridePendingTransition(0, 0);
    }
}
