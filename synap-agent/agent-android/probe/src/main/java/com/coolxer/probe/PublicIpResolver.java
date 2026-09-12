package com.coolxer.probe;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class PublicIpResolver {
    private static final String[] ENDPOINTS = {
            "https://api.ipify.org",
            "https://api64.ipify.org"
    };
    private static final int TIMEOUT_MILLIS = 4_000;

    private PublicIpResolver() { }

    static String resolve() {
        for (String endpoint : ENDPOINTS) {
            String address = request(endpoint);
            if (isPublicAddress(address)) {
                return normalize(address);
            }
        }
        return null;
    }

    private static String request(String endpoint) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(TIMEOUT_MILLIS);
            connection.setReadTimeout(TIMEOUT_MILLIS);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "text/plain");
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return null;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8))) {
                String value = reader.readLine();
                return value == null || value.length() > 64 ? null : value.trim();
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    static boolean isPublicAddress(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(normalized);
            if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                    || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                    || address.isMulticastAddress()) {
                return false;
            }
            byte[] bytes = address.getAddress();
            if (bytes.length == 4) {
                int first = bytes[0] & 0xff;
                int second = bytes[1] & 0xff;
                return first != 0 && !(first == 100 && second >= 64 && second <= 127);
            }
            return address instanceof Inet6Address && (bytes[0] & 0xfe) != 0xfc;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String address = value.trim();
        if (address.isEmpty() || address.length() > 64) {
            return null;
        }
        if (address.indexOf(':') < 0) {
            String[] parts = address.split("\\.", -1);
            if (parts.length != 4) {
                return null;
            }
            for (String part : parts) {
                if (!part.matches("\\d{1,3}")) {
                    return null;
                }
                int octet = Integer.parseInt(part);
                if (octet > 255) {
                    return null;
                }
            }
        }
        return address;
    }
}
