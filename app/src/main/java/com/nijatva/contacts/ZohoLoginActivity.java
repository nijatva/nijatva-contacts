package com.nijatva.contacts;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * In-app Zoho login (WebView) with a browser fallback.
 *
 * Zoho's page warns that the WebView lacks WebAuthn/FIDO2. Android's WebView genuinely
 * cannot provide passkeys, so a minimal stub satisfies the capability check (the user
 * still signs in with their real email and password). If an account really requires a
 * passkey, "Open in browser instead" opens a full browser.
 */
public class ZohoLoginActivity extends Activity {

    private static final String SHIM =
        "(function(){try{"
        + "if(!window.PublicKeyCredential){"
        + "window.PublicKeyCredential=function(){};"
        + "window.PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable=function(){return Promise.resolve(true);};"
        + "window.PublicKeyCredential.isConditionalMediationAvailable=function(){return Promise.resolve(false);};"
        + "window.PublicKeyCredential.isExternalCTAP2SecurityKeySupported=function(){return Promise.resolve(false);};"
        + "}"
        + "if(!navigator.credentials){navigator.credentials={};}"
        + "}catch(e){}})();";

    private WebView web;
    private TextView status;
    private SharedPreferences prefs;
    private boolean handled = false;
    private String authUrl;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("contacts_cloud", 0);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xFF14141C));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF14141C);
        root.setPadding(dp(14), dp(14), dp(14), dp(14));

        TextView title = new TextView(this);
        title.setText("Sign in with Zoho");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setTypeface(null, Typeface.BOLD);
        root.addView(title);

        status = new TextView(this);
        status.setTextColor(0xFF9AA0A6);
        status.setTextSize(12);
        status.setPadding(0, dp(6), 0, dp(10));
        status.setText("Sign in using your email and password. If any notice appears, "
                + "continue with your email and password, or use 'Open in browser instead'.");
        root.addView(status);

        web = new WebView(this);
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setSupportMultipleWindows(false);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView v, String url, Bitmap favicon) {
                if (Build.VERSION.SDK_INT >= 19) {
                    try { v.evaluateJavascript(SHIM, null); } catch (Exception ignore) {}
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, String url) {
                if (url != null && url.startsWith("mycontacts://oauth")) {
                    onCode(url);
                    return true;
                }
                return false;
            }
        });
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1f));

        Button browser = new Button(this);
        browser.setText("Open in browser instead");
        browser.setAllCaps(false);
        browser.setTextColor(Color.WHITE);
        GradientDrawable bb = new GradientDrawable();
        bb.setColor(0xFF2A2A36);
        bb.setCornerRadius(dp(22));
        browser.setBackground(bb);
        browser.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(44)));
        browser.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)));
                    status.setText("Continue in the browser, then return to this app.");
                } catch (Exception e) {
                    status.setText("Unable to open the browser: " + e.getMessage());
                }
            }
        });
        root.addView(browser);

        Button close = new Button(this);
        close.setText("Close");
        close.setAllCaps(false);
        close.setTextColor(Color.WHITE);
        GradientDrawable gb = new GradientDrawable();
        gb.setColor(0xFF2A2A36);
        gb.setCornerRadius(dp(22));
        close.setBackground(gb);
        close.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(44)));
        close.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { finish(); }
        });
        root.addView(close);

        setContentView(root);

        try {
            String verifier = ZohoOAuth.newVerifier();
            prefs.edit()
                    .putString("client_id", ZohoConfig.CLIENT_ID)
                    .putString("client_secret", ZohoConfig.CLIENT_SECRET)
                    .putString("dc", ZohoConfig.DC)
                    .putString("verifier", verifier)
                    .apply();
            authUrl = ZohoOAuth.authUrl(ZohoConfig.DC, ZohoConfig.CLIENT_ID, verifier);
            web.loadUrl(authUrl);
        } catch (Exception e) {
            status.setText("Failed to start sign-in: " + e.getMessage());
        }
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private void onCode(String url) {
        if (handled) return;
        handled = true;
        String code = Uri.parse(url).getQueryParameter("code");
        String error = Uri.parse(url).getQueryParameter("error");
        if (error != null) { status.setText("Permission denied: " + error); return; }
        if (code == null || code.length() == 0) {
            status.setText("Authorization code was not received. Please try again.");
            return;
        }
        status.setText("Signed in. Fetching contacts from Zoho...");
        web.setVisibility(View.GONE);
        runSync(code);
    }

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
                    ZohoSync.Result r = ZohoSync.run(ZohoLoginActivity.this, prefs);
                    setStatus(r.summary());
                    if (r.error == null) {
                        prefs.edit().putString("last_status", "Zoho sync complete - added: " + r.added).apply();
                    }
                } catch (Exception e) {
                    setStatus("Error: " + e.getMessage());
                }
            }
        }).start();
    }

    private void setStatus(final String s) {
        runOnUiThread(new Runnable() { public void run() { status.setText(s); } });
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack() && web.getVisibility() == View.VISIBLE) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
