package com.nijatva.contacts;

import java.util.ArrayList;
import java.util.List;

/** Minimal vCard 3.0/4.0 parser: FN, N, TEL, EMAIL */
public class VCardParser {

    public static class Card {
        public String name = "";
        public List<String> phones = new ArrayList<String>();
        public List<String> emails = new ArrayList<String>();
    }

    public static List<Card> parse(String text) {
        List<Card> out = new ArrayList<Card>();
        if (text == null) return out;

        // unfold lines (RFC 2426 folding)
        String[] raw = text.replace("\r\n", "\n").replace("\r", "\n").split("\n");
        List<String> lines = new ArrayList<String>();
        for (String r : raw) {
            if (r.length() > 0 && (r.charAt(0) == ' ' || r.charAt(0) == '\t') && lines.size() > 0) {
                lines.set(lines.size() - 1, lines.get(lines.size() - 1) + r.substring(1));
            } else {
                lines.add(r);
            }
        }

        Card cur = null;
        for (String line : lines) {
            String up = line.toUpperCase();
            if (up.startsWith("BEGIN:VCARD")) { cur = new Card(); continue; }
            if (up.startsWith("END:VCARD")) { if (cur != null) out.add(cur); cur = null; continue; }
            if (cur == null) continue;

            int colon = line.indexOf(':');
            if (colon < 0) continue;
            String left = line.substring(0, colon);
            String value = line.substring(colon + 1).trim();
            String prop = left;
            int semi = left.indexOf(';');
            if (semi >= 0) prop = left.substring(0, semi);
            prop = prop.toUpperCase();

            if (prop.equals("FN")) {
                if (cur.name.length() == 0) cur.name = value;
            } else if (prop.equals("N")) {
                // Last;First;Middle;Prefix;Suffix
                String[] p = value.split(";");
                if (cur.name.length() == 0) {
                    String first = p.length > 1 ? p[1].trim() : "";
                    String last = p.length > 0 ? p[0].trim() : "";
                    String full = (first + " " + last).trim();
                    if (full.length() == 0 && p.length > 0) full = p[0].trim();
                    cur.name = full;
                }
            } else if (prop.equals("TEL")) {
                String v = value.replace(" ", "").replace("-", "").replace("(", "").replace(")", "");
                if (v.length() > 0) cur.phones.add(v);
            } else if (prop.equals("EMAIL")) {
                if (value.length() > 0) cur.emails.add(value);
            }
        }
        return out;
    }

    /** digits only, last 10 chars - used for de-duplication */
    public static String norm(String number) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < number.length(); i++) {
            char c = number.charAt(i);
            if (c >= '0' && c <= '9') sb.append(c);
        }
        String d = sb.toString();
        return d.length() > 10 ? d.substring(d.length() - 10) : d;
    }
}
