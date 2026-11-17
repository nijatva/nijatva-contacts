package com.nijatva.contacts;

import android.util.Base64;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.SecureRandom;
import org.json.JSONObject;

/** Zoho OAuth 2.0 (authorization code + PKCE) for mobile apps. */
public class ZohoOAuth {

    public static final String REDIRECT = "mycontacts://oauth";
    public static final String SCOPE = "zohocontacts.contactapi.READ";

    public static String accountsUrl(String dc) {
        String d = (dc == null || dc.trim().length() == 0) ? "in" : dc.trim();
        return "https://accounts.zoho." + d;
    }

    /** Random PKCE code verifier. */
    public static String newVerifier() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~";
        SecureRandom r = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 64; i++) sb.append(chars.charAt(r.nextInt(chars.length())));
        return sb.toString();
    }

    /** S256 code challenge for the verifier. */
    public static String challenge(String verifier) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] d = md.digest(verifier.getBytes("UTF-8"));
        return Base64.encodeToString(d, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    public static String authUrl(String dc, String clientId, String verifier) throws Exception {
        StringBuilder u = new StringBuilder();
        u.append(accountsUrl(dc)).append("/oauth/v2/auth?");
        u.append("scope=").append(enc(SCOPE));
        u.append("&client_id=").append(enc(clientId));
        u.append("&response_type=code");
        u.append("&redirect_uri=").append(enc(REDIRECT));
        u.append("&access_type=offline");
        u.append("&prompt=consent");
        u.append("&code_challenge=").append(enc(challenge(verifier)));
        u.append("&code_challenge_method=S256");
        return u.toString();
    }

    /** Exchanges the authorization code for tokens. Returns {access_token, refresh_token}. */
    public static String[] exchange(String dc, String clientId, String clientSecret,
                                    String code, String verifier) throws Exception {
        StringBuilder form = new StringBuilder();
        form.append("grant_type=authorization_code");
        form.append("&client_id=").append(enc(clientId));
        if (clientSecret != null && clientSecret.length() > 0) form.append("&client_secret=").append(enc(clientSecret));
        form.append("&code=").append(enc(code));
        form.append("&redirect_uri=").append(enc(REDIRECT));
        form.append("&code_verifier=").append(enc(verifier));

        HttpJson.Resp r = HttpJson.request("POST", accountsUrl(dc) + "/oauth/v2/token",
                form.toString(), "application/x-www-form-urlencoded", null);
        String b = r.body == null ? "" : r.body.trim();
        if (b.length() == 0) {
            throw new Exception("Empty response from the token server (HTTP " + r.code + ")");
        }
        if (!b.startsWith("{")) {
            throw new Exception("Token server returned a non-JSON response (HTTP " + r.code + "): " + clean(b));
        }
        JSONObject o = new JSONObject(b);
        if (o.has("error")) {
            throw new Exception("Token error: " + o.optString("error")
                    + " (please verify the client ID, redirect URI and client secret)");
        }
        String at = o.optString("access_token", "");
        if (at.length() == 0) {
            throw new Exception("Access token not received: " + b.substring(0, Math.min(160, b.length())));
        }
        return new String[]{at, o.optString("refresh_token", "")};
    }

    /** Uses the stored refresh token to obtain a fresh access token. */
    public static String[] refresh(String dc, String clientId, String clientSecret,
                                   String refreshToken) throws Exception {
        StringBuilder form = new StringBuilder();
        form.append("grant_type=refresh_token");
        form.append("&client_id=").append(enc(clientId));
        if (clientSecret != null && clientSecret.length() > 0) form.append("&client_secret=").append(enc(clientSecret));
        form.append("&refresh_token=").append(enc(refreshToken));

        HttpJson.Resp r = HttpJson.request("POST", accountsUrl(dc) + "/oauth/v2/token",
                form.toString(), "application/x-www-form-urlencoded", null);
        String b = r.body == null ? "" : r.body.trim();
        if (!b.startsWith("{")) {
            throw new Exception("Token refresh failed (HTTP " + r.code + "): " + clean(b));
        }
        JSONObject o = new JSONObject(b);
        if (o.has("error")) throw new Exception("Refresh error: " + o.optString("error"));
        String at = o.optString("access_token", "");
        if (at.length() == 0) throw new Exception("Refresh returned no access token");
        return new String[]{at, o.optString("refresh_token", refreshToken)};
    }

    private static String enc(String s) throws UnsupportedEncodingException {
        return URLEncoder.encode(s, "UTF-8");
    }

    /** Strips style/script blocks and tags so server errors are readable. */
    private static String clean(String s) {
        String t = s.replaceAll("(?s)<style[^>]*>.*?</style>", " ");
        t = t.replaceAll("(?s)<script[^>]*>.*?</script>", " ");
        t = t.replaceAll("(?s)<[^>]*>", " ").replaceAll("\\s+", " ").trim();
        if (t.length() == 0) t = s;
        return t.length() > 400 ? t.substring(0, 400) : t;
    }
}
