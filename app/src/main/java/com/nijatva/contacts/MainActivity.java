package com.nijatva.contacts;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/** Dark contacts list with Favourites, Groups and sorting. */
public class MainActivity extends Activity {

    private static final int REQ_PERM = 5;
    private static final int BG = 0xFF14141C;
    private static final int ROW = 0xFF1E1E2A;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GREY = 0xFF9AA0A6;
    private static final int BLUE = 0xFF5B8DEF;

    private static final int[] AV = {0xFFE57373, 0xFFF06292, 0xFFBA68C8, 0xFF9575CD, 0xFF7986CB,
            0xFF64B5F6, 0xFF4FC3F7, 0xFF4DB6AC, 0xFF81C784, 0xFFAED581, 0xFFFFB74D, 0xFFA1887F};

    static class Contact {
        long id;
        String name;
        String phone;
        boolean starred;
        String photoUri;
        String initials;
        int color;
        HashSet<Long> groups = new HashSet<Long>();
    }

    static class Group {
        long id;
        String title;
        int count;
    }

    private final List<Object> shown = new ArrayList<Object>();
    private final List<Contact> allContacts = new ArrayList<Contact>();
    private final List<Group> allGroups = new ArrayList<Group>();
    private ContactsAdapter adapter;
    private EditText search;
    private TextView tabContacts, tabFav, tabGroups, chip, countView;
    private int mode = 0;
    private long groupFilter = -1;
    private String groupName = "";
    private String sort = "first";
    private SharedPreferences prefs;
    private final HashMap<Long, Bitmap> photoCache = new HashMap<Long, Bitmap>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("contacts_prefs", 0);
        sort = prefs.getString("sort", "first");
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(BG));
        setContentView(buildUi());
        if (Build.VERSION.SDK_INT >= 23) {
            requestPermissions(new String[]{android.Manifest.permission.READ_CONTACTS,
                    android.Manifest.permission.WRITE_CONTACTS}, REQ_PERM);
        } else {
            load();
        }
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout srow = new LinearLayout(this);
        srow.setOrientation(LinearLayout.HORIZONTAL);
        srow.setGravity(Gravity.CENTER_VERTICAL);
        srow.setPadding(dp(14), dp(14), dp(14), dp(6));

        search = new EditText(this);
        search.setHint("Search contacts");
        search.setHintTextColor(GREY);
        search.setTextColor(WHITE);
        search.setTextSize(15);
        search.setSingleLine(true);
        GradientDrawable sb = new GradientDrawable();
        sb.setColor(0xFF2A2A36);
        sb.setCornerRadius(dp(26));
        search.setBackground(sb);
        search.setPadding(dp(20), dp(12), dp(20), dp(12));
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) { applyFilter(); }
        });
        srow.addView(search, new LinearLayout.LayoutParams(0, -2, 1f));

        ImageView gear = new ImageView(this);
        gear.setImageResource(android.R.drawable.ic_menu_preferences);
        gear.setColorFilter(GREY);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(dp(40), dp(40));
        glp.leftMargin = dp(10);
        gear.setLayoutParams(glp);
        gear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { openSettings(); }
        });
        srow.addView(gear);
        col.addView(srow);

        LinearLayout frow = new LinearLayout(this);
        frow.setOrientation(LinearLayout.HORIZONTAL);
        frow.setGravity(Gravity.CENTER_VERTICAL);
        frow.setPadding(dp(16), dp(4), dp(16), dp(8));

        chip = new TextView(this);
        chip.setText("  All contacts  \u25BE");
        chip.setTextColor(WHITE);
        chip.setTextSize(14);
        GradientDrawable cb = new GradientDrawable();
        cb.setColor(0xFF2A2A36);
        cb.setCornerRadius(dp(18));
        chip.setBackground(cb);
        chip.setPadding(dp(14), dp(8), dp(14), dp(8));
        chip.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { mode = 0; groupFilter = -1; setTabs(); applyFilter(); }
        });
        frow.addView(chip);

        countView = new TextView(this);
        countView.setTextColor(GREY);
        countView.setTextSize(12);
        countView.setPadding(dp(12), 0, 0, 0);
        frow.addView(countView);
        col.addView(frow);

        ListView list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setBackgroundColor(BG);
        adapter = new ContactsAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            public void onItemClick(android.widget.AdapterView<?> p, View v, int pos, long id) {
                Object o = shown.get(pos);
                if (o instanceof Contact) openContact((Contact) o);
                else if (o instanceof Group) {
                    Group g = (Group) o;
                    mode = 3;
                    groupFilter = g.id;
                    groupName = g.title;
                    setTabs();
                    applyFilter();
                }
            }
        });
        col.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setBackgroundColor(0xFF1A1A24);
        nav.setPadding(dp(8), dp(8), dp(8), dp(8));
        tabContacts = tab(nav, android.R.drawable.ic_menu_myplaces, "Contacts");
        tabFav = tab(nav, android.R.drawable.btn_star_big_off, "Favourites");
        tabGroups = tab(nav, android.R.drawable.ic_menu_agenda, "Groups");
        tabContacts.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { mode = 0; groupFilter = -1; setTabs(); applyFilter(); }
        });
        tabFav.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { mode = 1; groupFilter = -1; setTabs(); applyFilter(); }
        });
        tabGroups.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { mode = 2; groupFilter = -1; setTabs(); applyFilter(); }
        });
        col.addView(nav, new LinearLayout.LayoutParams(-1, -2));

        TextView fab = new TextView(this);
        fab.setText("+");
        fab.setTextColor(Color.WHITE);
        fab.setTextSize(28);
        fab.setGravity(Gravity.CENTER);
        GradientDrawable fb = new GradientDrawable();
        fb.setColor(BLUE);
        fb.setShape(GradientDrawable.OVAL);
        fab.setBackground(fb);
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(dp(56), dp(56));
        flp.gravity = Gravity.BOTTOM | Gravity.END;
        flp.rightMargin = dp(18);
        flp.bottomMargin = dp(78);
        fab.setLayoutParams(flp);
        fab.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI));
                } catch (Exception e) {
                    toast("Unable to open the add-contact screen");
                }
            }
        });
        root.addView(fab);

        setTabs();
        return root;
    }

    private TextView tab(LinearLayout parent, int icon, String label) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(6), dp(6), dp(6), dp(6));
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        iv.setColorFilter(GREY);
        t.addView(iv, new LinearLayout.LayoutParams(dp(24), dp(24)));
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(GREY);
        tv.setTextSize(11);
        tv.setGravity(Gravity.CENTER);
        t.addView(tv);
        t.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        parent.addView(t);
        return tv;
    }

    private void setTabs() {
        tabContacts.setTextColor(mode == 0 || mode == 3 ? BLUE : GREY);
        tabFav.setTextColor(mode == 1 ? BLUE : GREY);
        tabGroups.setTextColor(mode == 2 ? BLUE : GREY);
    }

    private void load() {
        new Thread(new Runnable() {
            public void run() {
                List<Contact> contacts = new ArrayList<Contact>();
                List<Group> groups = new ArrayList<Group>();
                try {
                    HashMap<Long, String> phones = new HashMap<Long, String>();
                    Cursor pc = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            new String[]{ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                                    ContactsContract.CommonDataKinds.Phone.NUMBER}, null, null, null);
                    if (pc != null) {
                        while (pc.moveToNext()) {
                            long cid = pc.getLong(0);
                            if (!phones.containsKey(cid)) phones.put(cid, pc.getString(1));
                        }
                        pc.close();
                    }

                    HashMap<Long, HashSet<Long>> memberOf = new HashMap<Long, HashSet<Long>>();
                    HashMap<Long, Integer> groupCount = new HashMap<Long, Integer>();
                    Cursor mc = getContentResolver().query(ContactsContract.Data.CONTENT_URI,
                            new String[]{ContactsContract.Data.CONTACT_ID,
                                    ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID},
                            ContactsContract.Data.MIMETYPE + "=?",
                            new String[]{ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE}, null);
                    if (mc != null) {
                        while (mc.moveToNext()) {
                            long cid = mc.getLong(0);
                            long gid = mc.getLong(1);
                            HashSet<Long> s = memberOf.get(cid);
                            if (s == null) { s = new HashSet<Long>(); memberOf.put(cid, s); }
                            s.add(gid);
                            Integer n = groupCount.get(gid);
                            groupCount.put(gid, n == null ? 1 : n + 1);
                        }
                        mc.close();
                    }

                    Cursor c = getContentResolver().query(ContactsContract.Contacts.CONTENT_URI,
                            new String[]{ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME,
                                    ContactsContract.Contacts.STARRED, ContactsContract.Contacts.PHOTO_URI},
                            null, null, ContactsContract.Contacts.DISPLAY_NAME + " ASC");
                    if (c != null) {
                        while (c.moveToNext()) {
                            Contact k = new Contact();
                            k.id = c.getLong(0);
                            k.name = c.getString(1);
                            if (k.name == null || k.name.trim().length() == 0) k.name = "(no name)";
                            k.starred = c.getInt(2) == 1;
                            k.photoUri = c.getString(3);
                            k.phone = phones.get(k.id);
                            k.initials = initialsOf(k.name);
                            k.color = colorFor(k.name);
                            HashSet<Long> gs = memberOf.get(k.id);
                            if (gs != null) k.groups = gs;
                            contacts.add(k);
                        }
                        c.close();
                    }

                    Cursor gc = getContentResolver().query(ContactsContract.Groups.CONTENT_URI,
                            new String[]{ContactsContract.Groups._ID, ContactsContract.Groups.TITLE},
                            ContactsContract.Groups.DELETED + "=0", null, ContactsContract.Groups.TITLE + " ASC");
                    if (gc != null) {
                        while (gc.moveToNext()) {
                            String title = gc.getString(1);
                            if (title == null || title.trim().length() == 0) continue;
                            Group g = new Group();
                            g.id = gc.getLong(0);
                            g.title = title;
                            Integer n = groupCount.get(g.id);
                            g.count = n == null ? 0 : n;
                            groups.add(g);
                        }
                        gc.close();
                    }
                } catch (Exception ignore) {}

                final List<Contact> fc = contacts;
                final List<Group> fg = groups;
                runOnUiThread(new Runnable() {
                    public void run() {
                        allContacts.clear();
                        allContacts.addAll(fc);
                        allGroups.clear();
                        allGroups.addAll(fg);
                        applyFilter();
                    }
                });
            }
        }).start();
    }

    private Comparator<Contact> bySort() {
        return new Comparator<Contact>() {
            public int compare(Contact a, Contact b) {
                String x = a.name, y = b.name;
                if ("last".equals(sort)) { x = surname(a.name); y = surname(b.name); }
                return x.compareToIgnoreCase(y);
            }
        };
    }

    private static String surname(String name) {
        String[] p = name.trim().split("\\s+");
        return p.length > 1 ? p[p.length - 1] : name;
    }

    private void applyFilter() {
        String q = search.getText().toString().trim().toLowerCase();
        shown.clear();

        if (mode == 2 && q.length() == 0) {
            shown.add("Groups");
            for (Group g : allGroups) shown.add(g);
            chip.setText("  All contacts  \u25BE");
            countView.setText(allGroups.size() + " groups");
            adapter.notifyDataSetChanged();
            return;
        }

        List<Contact> base = new ArrayList<Contact>();
        for (Contact k : allContacts) {
            if (mode == 1 && !k.starred) continue;
            if (mode == 3 && !k.groups.contains(groupFilter)) continue;
            if (q.length() > 0) {
                boolean m = k.name.toLowerCase().contains(q)
                        || (k.phone != null && k.phone.replace(" ", "").contains(q));
                if (!m) continue;
            }
            base.add(k);
        }
        Collections.sort(base, bySort());

        if (mode == 3) chip.setText("  " + groupName + "  \u25BE");
        else if (mode == 1) chip.setText("  Favourites  \u25BE");
        else chip.setText("  All contacts  \u25BE");

        if (q.length() > 0) {
            shown.add("Search results");
            shown.addAll(base);
        } else if (mode == 1) {
            shown.add("\u2605  Favourites");
            shown.addAll(base);
        } else {
            String last = "";
            for (Contact k : base) {
                String letter = letterOf("last".equals(sort) ? surname(k.name) : k.name);
                if (!letter.equals(last)) { shown.add(letter); last = letter; }
                shown.add(k);
            }
        }
        countView.setText(base.size() + " contacts");
        adapter.notifyDataSetChanged();
    }

    private void openContact(Contact k) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setData(android.content.ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, k.id));
            startActivity(i);
        } catch (Exception e) {
            toast("Unable to open this contact");
        }
    }

    private void openSettings() { startActivity(new Intent(this, SettingsActivity.class)); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private static String initialsOf(String name) {
        String[] p = name.trim().split("\\s+");
        String a = p.length > 0 && p[0].length() > 0 ? p[0].substring(0, 1) : "?";
        String b = p.length > 1 && p[1].length() > 0 ? p[1].substring(0, 1) : "";
        return (a + b).toUpperCase();
    }

    private static int colorFor(String name) {
        int h = 0;
        for (int i = 0; i < name.length(); i++) h = h * 31 + name.charAt(i);
        return AV[Math.abs(h) % AV.length];
    }

    private static String letterOf(String name) {
        if (name == null || name.length() == 0) return "#";
        char ch = Character.toUpperCase(name.charAt(0));
        return (ch >= 'A' && ch <= 'Z') ? String.valueOf(ch) : "#";
    }

    private Bitmap avatar(Contact k, int size) {
        if (k.photoUri != null) {
            Bitmap cached = photoCache.get(k.id);
            if (cached != null) return cached;
            try {
                InputStream is = getContentResolver().openInputStream(Uri.parse(k.photoUri));
                Bitmap bm = BitmapFactory.decodeStream(is);
                if (is != null) is.close();
                if (bm != null) {
                    Bitmap circ = circle(bm, size);
                    if (photoCache.size() > 250) photoCache.clear();
                    photoCache.put(k.id, circ);
                    return circ;
                }
            } catch (Exception ignore) {}
        }
        return initials(k.initials, k.color, size);
    }

    private Bitmap circle(Bitmap src, int size) {
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        BitmapShader sh = new BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        Matrix m = new Matrix();
        float scale = (float) size / Math.min(src.getWidth(), src.getHeight());
        m.setScale(scale, scale);
        sh.setLocalMatrix(m);
        p.setShader(sh);
        c.drawCircle(size / 2f, size / 2f, size / 2f, p);
        return out;
    }

    private Bitmap initials(String text, int color, int size) {
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        c.drawCircle(size / 2f, size / 2f, size / 2f, p);
        p.setColor(Color.WHITE);
        p.setTextSize(size * 0.38f);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        float y = size / 2f - (p.descent() + p.ascent()) / 2f;
        c.drawText(text, size / 2f, y, p);
        return out;
    }

    class ContactsAdapter extends BaseAdapter {
        public int getCount() { return shown.size(); }
        public Object getItem(int p) { return shown.get(p); }
        public long getItemId(int p) { return p; }
        public int getViewTypeCount() { return 3; }
        public int getItemViewType(int p) {
            Object o = shown.get(p);
            if (o instanceof String) return 0;
            if (o instanceof Group) return 2;
            return 1;
        }

        public View getView(int p, View reuse, ViewGroup parent) {
            Object o = shown.get(p);
            if (o instanceof String) {
                TextView t = new TextView(MainActivity.this);
                t.setText((String) o);
                t.setTextColor(GREY);
                t.setTextSize(13);
                t.setTypeface(null, Typeface.BOLD);
                t.setPadding(dp(22), dp(16), dp(16), dp(8));
                return t;
            }
            if (o instanceof Group) {
                Group g = (Group) o;
                LinearLayout wrap = new LinearLayout(MainActivity.this);
                wrap.setOrientation(LinearLayout.VERTICAL);
                wrap.setPadding(dp(14), dp(3), dp(14), dp(3));
                LinearLayout row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(ROW);
                bg.setCornerRadius(dp(16));
                row.setBackground(bg);
                row.setPadding(dp(12), dp(10), dp(12), dp(10));
                ImageView av = new ImageView(MainActivity.this);
                av.setImageBitmap(initials(initialsOf(g.title), colorFor(g.title), dp(46)));
                row.addView(av, new LinearLayout.LayoutParams(dp(46), dp(46)));
                LinearLayout col = new LinearLayout(MainActivity.this);
                col.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, -2, 1f);
                clp.leftMargin = dp(14);
                col.setLayoutParams(clp);
                TextView name = new TextView(MainActivity.this);
                name.setText(g.title);
                name.setTextColor(WHITE);
                name.setTextSize(16);
                col.addView(name);
                TextView sub = new TextView(MainActivity.this);
                sub.setText(g.count + " contacts");
                sub.setTextColor(GREY);
                sub.setTextSize(12);
                col.addView(sub);
                row.addView(col);
                wrap.addView(row, new LinearLayout.LayoutParams(-1, -2));
                return wrap;
            }

            Contact k = (Contact) o;
            LinearLayout wrap = new LinearLayout(MainActivity.this);
            wrap.setOrientation(LinearLayout.VERTICAL);
            wrap.setPadding(dp(14), dp(3), dp(14), dp(3));
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(ROW);
            bg.setCornerRadius(dp(16));
            row.setBackground(bg);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            ImageView av = new ImageView(MainActivity.this);
            av.setImageBitmap(avatar(k, dp(46)));
            row.addView(av, new LinearLayout.LayoutParams(dp(46), dp(46)));
            LinearLayout col = new LinearLayout(MainActivity.this);
            col.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, -2, 1f);
            clp.leftMargin = dp(14);
            col.setLayoutParams(clp);
            TextView name = new TextView(MainActivity.this);
            name.setText(k.name);
            name.setTextColor(WHITE);
            name.setTextSize(16);
            col.addView(name);
            if (k.phone != null && k.phone.length() > 0) {
                TextView ph = new TextView(MainActivity.this);
                ph.setText(k.phone);
                ph.setTextColor(GREY);
                ph.setTextSize(12);
                col.addView(ph);
            }
            row.addView(col);
            wrap.addView(row, new LinearLayout.LayoutParams(-1, -2));
            return wrap;
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        if (req == REQ_PERM) {
            for (int r : res) {
                if (r != PackageManager.PERMISSION_GRANTED) {
                    toast("Contacts permission is required");
                    return;
                }
            }
            load();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        sort = prefs.getString("sort", "first");
        if (Build.VERSION.SDK_INT < 23
                || checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            load();
        }
    }
}
