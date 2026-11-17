package com.nijatva.contacts;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;

/**
 * Shared Zoho sync routine.
 * Uses the stored access token; if it has expired, refreshes it automatically
 * with the stored refresh token - so no repeated sign-in is needed.
 */
public class ZohoSync {

    public static class Result {
        public int fetched, added, skipped, failed;
        public String diag = "";
        public String error = null;

        public String summary() {
            if (error != null) return "Error: " + error;
            return "Sync complete.\n" + diag
                    + "\nAdded: " + added
                    + "\nAlready present: " + skipped
                    + (failed > 0 ? "\nFailed: " + failed : "")
                    + "\n\nOpen the contacts list to view them.";
        }
    }

    public static Result run(Context ctx, SharedPreferences prefs) {
        Result res = new Result();
        final String dc = prefs.getString("dc", ZohoConfig.DC);
        String token = prefs.getString("access_token", "");
        final String refresh = prefs.getString("refresh_token", "");

        if (token == null || token.length() == 0) {
            res.error = "Not signed in. Please use 'Sign in with Zoho' first.";
            return res;
        }

        ZohoContactsApi.Result r;
        try {
            r = ZohoContactsApi.fetch(dc, token);
        } catch (Exception e) {
            if (refresh != null && refresh.length() > 0) {
                try {
                    String[] t = ZohoOAuth.refresh(dc, ZohoConfig.CLIENT_ID, ZohoConfig.CLIENT_SECRET, refresh);
                    prefs.edit().putString("access_token", t[0]).putString("refresh_token", t[1]).apply();
                    r = ZohoContactsApi.fetch(dc, t[0]);
                } catch (Exception e2) {
                    res.error = e2.getMessage();
                    return res;
                }
            } else {
                res.error = e.getMessage();
                return res;
            }
        }

        res.diag = r.diag;
        res.fetched = r.cards.size();
        try {
            HashSet<String> seen = ContactInserter.existingNumbers(ctx);
            for (VCardParser.Card c : r.cards) {
                int rr = ContactInserter.insert(ctx, c, seen);
                if (rr == 1) res.added++;
                else if (rr == 0) res.skipped++;
                else res.failed++;
            }
        } catch (Exception e) {
            res.error = e.getMessage();
        }
        return res;
    }
}
