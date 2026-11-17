package com.nijatva.contacts;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Zoho Contacts REST API - Get Multiple Contacts (scope: zohocontacts.contactapi.READ). */
public class ZohoContactsApi {

    public static class Result {
        public List<VCardParser.Card> cards = new ArrayList<VCardParser.Card>();
        public String diag = "";
    }

    public static Result fetch(String dc, String accessToken) throws Exception {
        Result res = new Result();
        String d = (dc == null || dc.trim().length() == 0) ? "in" : dc.trim();
        int page = 1;
        int total = 0;

        while (true) {
            String url = "https://contacts.zoho." + d
                    + "/api/v1/accounts/self/contacts?include=emails,phones&page=" + page + "&per_page=100";
            HttpJson.Resp r = HttpJson.request("GET", url, null, null, "Zoho-oauthtoken " + accessToken);
            if (r.code == 401) throw new Exception("Session expired (401) - please sign in again");
            if (r.code != 200) throw new Exception("Contacts API error " + r.code + ": " + shortOf(r.body));

            JSONObject o;
            try {
                o = new JSONObject(r.body);
            } catch (Exception pe) {
                throw new Exception("Unable to parse the Zoho response (" + r.body.length()
                        + " characters received): " + shortOf(r.body));
            }

            JSONArray arr = o.optJSONArray("contacts");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject c = arr.optJSONObject(i);
                    if (c == null) continue;

                    VCardParser.Card card = new VCardParser.Card();
                    String first = c.optString("first_name", "");
                    String last = c.optString("last_name", "");
                    String name = (first + " " + last).trim();
                    if (name.length() == 0) name = c.optString("company", "");
                    card.name = name;

                    JSONArray ph = c.optJSONArray("phones");
                    if (ph != null) {
                        for (int j = 0; j < ph.length(); j++) {
                            JSONObject p = ph.optJSONObject(j);
                            if (p != null) {
                                String num = p.optString("number", "");
                                if (num.trim().length() > 0) card.phones.add(num.trim());
                            }
                        }
                    }

                    JSONArray em = c.optJSONArray("emails");
                    if (em != null) {
                        for (int j = 0; j < em.length(); j++) {
                            JSONObject e = em.optJSONObject(j);
                            if (e != null) {
                                String ad = e.optString("email_id", "");
                                if (ad.trim().length() > 0) card.emails.add(ad.trim());
                            }
                        }
                    }

                    res.cards.add(card);
                    total++;
                }
            }

            if (!o.optBoolean("has_more", false)) break;
            page++;
            if (page > 200) break;
        }

        res.diag = "Fetched from Zoho: " + total + " (page " + page + ")";
        return res;
    }

    private static String shortOf(String s) {
        if (s == null) return "";
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() > 160 ? t.substring(0, 160) : t;
    }
}
