package com.nijatva.contacts;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.List;

import com.nijatva.signature.Signature;
import com.nijatva.signature.SignatureTheme;

/** Settings: Display (sort), Contacts backup (VCF export/import), Cloud sync, Permissions, Advanced. */
public class SettingsActivity extends Activity {

    private static final int REQ_EXPORT = 31;
    private static final int REQ_IMPORT = 32;

    private static final int BG = 0xFF14141C;
    private static final int CARD = 0xFF1E1E2A;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GREY = 0xFF9AA0A6;
    private static final int BLUE = 0xFF5B8DEF;
    private static final int LABEL = 0xFFB39DDB;

    private TextView status, sortValue;
    private SharedPreferences prefs, cloud;
    private String exportData = null;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        // This app is dark by design, so the signature screens open dark too.
        SignatureTheme.seedDefault(this, SignatureTheme.DARK, SignatureTheme.GREEN);
        prefs = getSharedPreferences("contacts_prefs", 0);
        cloud = getSharedPreferences("contacts_cloud", 0);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(BG));
        setContentView(buildUi());
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private View buildUi() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(28));
        sv.addView(root, new ViewGroup.LayoutParams(-1, -2));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(0, 0, 0, dp(14));
        ImageView back = new ImageView(this);
        back.setImageResource(android.R.drawable.ic_menu_revert);
        back.setColorFilter(WHITE);
        back.setLayoutParams(new LinearLayout.LayoutParams(dp(32), dp(32)));
        back.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { finish(); }
        });
        head.addView(back);
        TextView title = new TextView(this);
        title.setText("Settings");
        title.setTextColor(WHITE);
        title.setTextSize(22);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(dp(14), 0, 0, 0);
        head.addView(title);
        root.addView(head);

        label(root, "Display");
        sortValue = item(root, "Sort by", sortLabel(), true);
        item(root, "Name format", "First name first", false);
        item(root, "Theme", "Dark", false);

        label(root, "Contacts backup");
        LinearLayout bc = cardBox();
        TextView bd = new TextView(this);
        bd.setText("Export all contacts to a .vcf file, or import contacts from a .vcf file.");
        bd.setTextColor(GREY);
        bd.setTextSize(12);
        bd.setPadding(0, 0, 0, dp(10));
        bc.addView(bd);

        Button exp = smallButton("Export contacts (.vcf)", BLUE);
        exp.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { startExport(); }
        });
        bc.addView(exp);

        Button imp = smallButton("Import contacts (.vcf)", 0xFF2A2A36);
        imp.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                it.addCategory(Intent.CATEGORY_OPENABLE);
                it.setType("*/*");
                try { startActivityForResult(it, REQ_IMPORT); }
                catch (Exception e) { setStatus("Unable to open the file picker"); }
            }
        });
        bc.addView(imp);
        root.addView(bc);

        label(root, "Cloud sync");
        LinearLayout c = cardBox();
        TextView t = new TextView(this);
        t.setText("Import contacts from Zoho");
        t.setTextColor(WHITE);
        t.setTextSize(16);
        t.setTypeface(null, Typeface.BOLD);
        c.addView(t);
        TextView d = new TextView(this);
        d.setText("Sign in once and your Zoho contacts will be added to this device. No setup required.");
        d.setTextColor(GREY);
        d.setTextSize(12);
        d.setPadding(0, dp(4), 0, dp(12));
        c.addView(d);

        Button signBtn = smallButton("Sign in with Zoho", BLUE);
        signBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(SettingsActivity.this, ZohoLoginActivity.class));
            }
        });
        c.addView(signBtn);

        Button syncNow = smallButton("Sync now (saved login)", 0xFF2A2A36);
        syncNow.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { runSyncNow(); }
        });
        c.addView(syncNow);

        TextView note = new TextView(this);
        note.setText("After signing in once, use 'Sync now' to refresh contacts without logging in again.");
        note.setTextColor(GREY);
        note.setTextSize(11);
        note.setPadding(0, dp(8), 0, 0);
        c.addView(note);

        root.addView(c);

        label(root, "Permissions");
        item(root, "Call logs", "Allowed", false);
        item(root, "SMS messages", "Not allowed", false);

        label(root, "Advanced");
        item(root, "View-only contacts from connected apps", "Show in list", false);

        label(root, "Nijatva");
        signatureRow(root, "Appearance", "Theme and accent colour", Signature.appearance(this));
        signatureRow(root, "Developer", "About the developer", Signature.developer(this));

        status = new TextView(this);
        status.setTextColor(GREY);
        status.setTextSize(12);
        status.setPadding(dp(6), dp(14), 0, 0);
        status.setText("");
        root.addView(status);

        TextView foot = new TextView(this);
        foot.setText("Privacy Policy  \u2022  Terms of Service");
        foot.setTextColor(GREY);
        foot.setTextSize(11);
        foot.setGravity(Gravity.CENTER);
        foot.setPadding(0, dp(24), 0, 0);
        root.addView(foot);

        return sv;
    }

    private String sortLabel() {
        return "last".equals(prefs.getString("sort", "first")) ? "Last name" : "First name";
    }

    private LinearLayout cardBox() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(16));
        c.setBackground(bg);
        c.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(14);
        c.setLayoutParams(lp);
        return c;
    }

    private void label(LinearLayout parent, String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(LABEL);
        t.setTextSize(12);
        t.setTypeface(null, Typeface.BOLD);
        t.setPadding(dp(6), dp(14), 0, dp(6));
        parent.addView(t);
    }

    /** A tappable row that opens one of the two screens every Nijatva app carries. */
    private void signatureRow(LinearLayout parent, String title, String subtitle,
                              final Intent target) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(14));
        card.setBackground(bg);
        card.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        card.setLayoutParams(lp);

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(WHITE);
        t.setTextSize(15);
        card.addView(t);

        TextView v = new TextView(this);
        v.setText(subtitle);
        v.setTextColor(GREY);
        v.setTextSize(12);
        v.setPadding(0, dp(2), 0, 0);
        card.addView(v);

        card.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                startActivity(target);
            }
        });
        parent.addView(card);
    }

    private TextView item(LinearLayout parent, String title, String value, boolean clickable) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(14));
        card.setBackground(bg);
        card.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        card.setLayoutParams(lp);

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(WHITE);
        t.setTextSize(15);
        card.addView(t);

        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(GREY);
        v.setTextSize(12);
        v.setPadding(0, dp(2), 0, 0);
        card.addView(v);

        if (clickable) {
            final String name = title;
            card.setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) {
                    if ("Sort by".equals(name)) showSortDialog();
                    else Toast.makeText(SettingsActivity.this, name + " - coming soon", Toast.LENGTH_SHORT).show();
                }
            });
        }
        parent.addView(card);
        return v;
    }

    private Button smallButton(String text, int color) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(22));
        b.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(44));
        lp.bottomMargin = dp(8);
        b.setLayoutParams(lp);
        return b;
    }

    private void showSortDialog() {
        final String[] options = {"First name", "Last name"};
        int checked = "last".equals(prefs.getString("sort", "first")) ? 1 : 0;
        new AlertDialog.Builder(this)
                .setTitle("Sort by")
                .setSingleChoiceItems(options, checked, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        prefs.edit().putString("sort", which == 1 ? "last" : "first").apply();
                        sortValue.setText(sortLabel());
                        d.dismiss();
                        setStatus("Sorting updated to " + options[which].toLowerCase() + ".");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startExport() {
        setStatus("Preparing contacts...");
        new Thread(new Runnable() {
            public void run() {
                final String data = buildVcf();
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (data == null || data.length() == 0) { setStatus("No contacts to export."); return; }
                        exportData = data;
                        Intent it = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                        it.addCategory(Intent.CATEGORY_OPENABLE);
                        it.setType("text/x-vcard");
                        it.putExtra(Intent.EXTRA_TITLE, "contacts.vcf");
                        try { startActivityForResult(it, REQ_EXPORT); }
                        catch (Exception e) { setStatus("Unable to open the save dialog"); }
                    }
                });
            }
        }).start();
    }

    private String buildVcf() {
        StringBuilder sb = new StringBuilder();
        Cursor c = null;
        try {
            c = getContentResolver().query(ContactsContract.Contacts.CONTENT_URI,
                    new String[]{ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME},
                    null, null, ContactsContract.Contacts.DISPLAY_NAME + " ASC");
            if (c == null) return "";
            while (c.moveToNext()) {
                long id = c.getLong(0);
                String name = c.getString(1);
                if (name == null) name = "";
                sb.append("BEGIN:VCARD\r\nVERSION:3.0\r\n");
                sb.append("FN:").append(name).append("\r\n");
                String[] parts = name.trim().split("\\s+");
                String last = parts.length > 1 ? parts[parts.length - 1] : "";
                String first = parts.length > 0 ? parts[0] : "";
                sb.append("N:").append(last).append(";").append(first).append(";;;\r\n");
                Cursor p = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER},
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID + "=?",
                        new String[]{String.valueOf(id)}, null);
                if (p != null) {
                    while (p.moveToNext()) {
                        String num = p.getString(0);
                        if (num != null && num.trim().length() > 0) {
                            sb.append("TEL;TYPE=CELL:").append(num.trim()).append("\r\n");
                        }
                    }
                    p.close();
                }
                Cursor e = getContentResolver().query(ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                        new String[]{ContactsContract.CommonDataKinds.Email.ADDRESS},
                        ContactsContract.CommonDataKinds.Email.CONTACT_ID + "=?",
                        new String[]{String.valueOf(id)}, null);
                if (e != null) {
                    while (e.moveToNext()) {
                        String ad = e.getString(0);
                        if (ad != null && ad.trim().length() > 0) {
                            sb.append("EMAIL:").append(ad.trim()).append("\r\n");
                        }
                    }
                    e.close();
                }
                sb.append("END:VCARD\r\n");
            }
        } catch (Exception ignore) {
        } finally {
            if (c != null) c.close();
        }
        return sb.toString();
    }

    private void writeExport(Uri uri) {
        try {
            OutputStream os = getContentResolver().openOutputStream(uri);
            os.write(exportData.getBytes("UTF-8"));
            os.flush();
            os.close();
            setStatus("Export complete. Contacts saved to the selected file.");
        } catch (Exception e) {
            setStatus("Export failed: " + e.getMessage());
        }
    }

    private void doImport(final Uri uri) {
        setStatus("Reading the file...");
        new Thread(new Runnable() {
            public void run() {
                try {
                    InputStream is = getContentResolver().openInputStream(uri);
                    BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String l;
                    while ((l = br.readLine()) != null) sb.append(l).append('\n');
                    br.close();
                    List<VCardParser.Card> cards = VCardParser.parse(sb.toString());
                    HashSet<String> seen = ContactInserter.existingNumbers(SettingsActivity.this);
                    int added = 0, skipped = 0, failed = 0;
                    for (VCardParser.Card card : cards) {
                        int r = ContactInserter.insert(SettingsActivity.this, card, seen);
                        if (r == 1) added++;
                        else if (r == 0) skipped++;
                        else failed++;
                    }
                    setStatus("Import complete.\nIn file: " + cards.size()
                            + "\nAdded: " + added
                            + "\nAlready present: " + skipped
                            + (failed > 0 ? "\nFailed: " + failed : ""));
                } catch (Exception e) {
                    setStatus("Import failed: " + e.getMessage());
                }
            }
        }).start();
    }

    private void runSyncNow() {
        setStatus("Syncing with Zoho...");
        new Thread(new Runnable() {
            public void run() {
                ZohoSync.Result r = ZohoSync.run(SettingsActivity.this, cloud);
                setStatus(r.summary());
            }
        }).start();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        if (req == REQ_EXPORT) writeExport(data.getData());
        else if (req == REQ_IMPORT) doImport(data.getData());
    }

    private void setStatus(final String s) {
        runOnUiThread(new Runnable() {
            public void run() {
                if (status != null) status.setText(s);
            }
        });
    }
}
