package com.nijatva.contacts;

import android.content.ContentProviderOperation;
import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract;
import java.util.ArrayList;
import java.util.HashSet;

/** Inserts parsed vCards into the phone's contact store (so My Dialer shows them). */
public class ContactInserter {

    /** Collect existing phone numbers (normalized) to avoid duplicates on re-sync. */
    public static HashSet<String> existingNumbers(Context ctx) {
        HashSet<String> set = new HashSet<String>();
        Cursor c = null;
        try {
            c = ctx.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER}, null, null, null);
            if (c != null) {
                while (c.moveToNext()) {
                    String n = c.getString(0);
                    if (n != null) set.add(VCardParser.norm(n));
                }
            }
        } catch (Exception e) {
        } finally {
            if (c != null) c.close();
        }
        return set;
    }

    /** @return 1 inserted, 0 skipped (duplicate), -1 failed */
    public static int insert(Context ctx, VCardParser.Card card, HashSet<String> seen) {
        // skip if any phone already exists
        for (String ph : card.phones) {
            if (seen.contains(VCardParser.norm(ph))) return 0;
        }
        if (card.name.trim().length() == 0 && card.phones.size() == 0) return 0;

        ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
        ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .build());

        String name = card.name.trim();
        if (name.length() > 0) {
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                    .build());
        }
        for (String ph : card.phones) {
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, ph)
                    .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                    .build());
        }
        for (String em : card.emails) {
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, em)
                    .withValue(ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Email.TYPE_HOME)
                    .build());
        }
        try {
            ctx.getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
            for (String ph : card.phones) seen.add(VCardParser.norm(ph));
            return 1;
        } catch (Exception e) {
            return -1;
        }
    }
}
