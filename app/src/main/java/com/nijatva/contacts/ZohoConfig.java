package com.nijatva.contacts;

/**
 * Zoho OAuth client configuration.
 *
 * These are PLACEHOLDERS for the public source. Create your own OAuth client at
 * https://api-console.zoho.in (client type: Mobile-based Applications, redirect URI
 * mycontacts://oauth, scope zohocontacts.contactapi.READ) and fill in your own values
 * to enable the optional Zoho contacts import.
 *
 * Never publish a client secret in a public repository. The app uses PKCE, so an
 * empty secret is sufficient for mobile clients.
 */
public class ZohoConfig {
    public static final String CLIENT_ID = "YOUR_CLIENT_ID";
    public static final String CLIENT_SECRET = "";
    public static final String DC = "in";
}
