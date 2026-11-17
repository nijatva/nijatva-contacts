package com.nijatva.contacts;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Receives mycontacts://oauth?code=... when the sign-in is completed in an external
 * browser, exchanges the code for tokens and runs the sync.
 */
public class OAuthRedirectActivity extends Activity {

    private TextView status;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("contacts_cloud", 0);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xFF14141C));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF14141C);
        root.setPadding(dp(20), dp(24), dp(20), dp(20));
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("Sign in with Zoho");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setTypeface(null, Typeface.BOLD);
        root.addView(title);

        status = new TextView(this);
        status.setTextColor(0xFF9AA0A6);
        status.setTextSize(13);
        status.setPadding(0, dp(12), 0, dp(20));
        status.setText("Completing sign-in...");
        root.addView(status);

        Button close = new Button(this);
        close.setText("Close");
        close.setAllCaps(false);
        close.setTextColor(Color.WHITE);
        GradientDrawable gb = new GradientDrawable();
        gb.setColor(0xFF2A2A36);
        gb.setCornerRadius(dp(22));
        close.setBackground(gb);
        close.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { finish(); }
        });
        root.addView(close);

        setContentView(root);

        Uri data = getIntent() != null ? getIntent().getData() : null;
        String code = data != null ? data.getQueryParameter("code") : null;
        String error = data != null ? data.getQueryParameter("error") : null;

        if (error != null) {
            setStatus("Permission denied: " + error);
        } else if (code == null || code.length() == 0) {
            setStatus("Authorization code was not received. Please try again.");
        } else {
            runSync(code);
        }
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private void runSync(final String code) {
        final String dc = prefs.getString("dc", ZohoConfig.DC);
        final String clientId = prefs.getString("client_id", ZohoConfig.CLIENT_ID);
        final String clientSecret = prefs.getString("client_secret", ZohoConfig.CLIENT_SECRET);
        final String verifier = prefs.getString("verifier", "");

        new Thread(new Runnable() {
            public void run() {
                try {
                    String[] tok = ZohoOAuth.exchange(dc, clientId, clientSecret, code, verifier);
                    prefs.edit().putString("access_token", tok[0]).putString("refresh_token", tok[1]).apply();
                    ZohoSync.Result r = ZohoSync.run(OAuthRedirectActivity.this, prefs);
                    setStatus(r.summary());
                } catch (Exception e) {
                    setStatus("Error: " + e.getMessage());
                }
            }
        }).start();
    }

    private void setStatus(final String s) {
        runOnUiThread(new Runnable() { public void run() { status.setText(s); } });
    }
}
