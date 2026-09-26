package com.exteragram.messenger.ai.network;

import android.content.SharedPreferences;

import com.exteragram.messenger.utils.network.ExteraHttpClient;
import com.exteragram.messenger.utils.network.RemoteUtils;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Dns;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ProxyDns implements Dns {

    public static final Dns INSTANCE = new ProxyDns();

    private static final String DEFAULT_DNS_URL = "https://dns.comss.one/dns-query";

    private final OkHttpClient client;
    private volatile String cachedUrl;
    private volatile boolean listenerInitialized = false;
    private SharedPreferences.OnSharedPreferenceChangeListener changeListener;

    private ProxyDns() {
        client = ExteraHttpClient.INSTANCE.getClient().newBuilder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build();
    }

    private void initializeListener() {
        if (listenerInitialized) {
            return;
        }
        RemoteUtils.initCached();
        if (RemoteUtils.sharedPreferences != null) {
            changeListener = (sharedPreferences, key) -> {
                if ("dns_url".equals(key)) {
                    cachedUrl = null;
                }
            };
            RemoteUtils.sharedPreferences.registerOnSharedPreferenceChangeListener(changeListener);
            listenerInitialized = true;
        }
    }

    private String getUrl() {
        if (!listenerInitialized) {
            initializeListener();
        }
        String url = cachedUrl;
        if (url != null) {
            return url;
        }
        url = RemoteUtils.getStringConfigValue("dns_url", DEFAULT_DNS_URL);
        cachedUrl = url;
        return url;
    }

    @Override
    public List<InetAddress> lookup(String hostname) throws UnknownHostException {
        try {
            List<String> addresses = resolveDns(hostname);
            if (addresses != null && !addresses.isEmpty()) {
                List<InetAddress> result = new ArrayList<>(addresses.size());
                for (String address : addresses) {
                    result.add(InetAddress.getByName(address));
                }
                return result;
            }
            return Dns.SYSTEM.lookup(hostname);
        } catch (Exception e) {
            return Dns.SYSTEM.lookup(hostname);
        }
    }

    private List<String> resolveDns(String hostname) {
        Request request = new Request.Builder()
                .url(getUrl())
                .post(RequestBody.create(buildDnsQuery(hostname), MediaType.parse("application/dns-message")))
                .addHeader("Accept", "application/dns-message")
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                return null;
            }
            return parseDnsResponse(response.body().bytes());
        } catch (IOException e) {
            return null;
        }
    }

    private byte[] buildDnsQuery(String hostname) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        try {
            out.writeShort(0x1234); // id
            out.writeShort(0x0100); // flags: recursion desired
            out.writeShort(1); // qdcount
            out.writeShort(0); // ancount
            out.writeShort(0); // nscount
            out.writeShort(0); // arcount
            for (String label : hostname.split("\\.")) {
                out.writeByte(label.length());
                out.writeBytes(label);
            }
            out.writeByte(0);
            out.writeShort(1); // type A
            out.writeShort(1); // class IN
        } catch (IOException ignore) {
        }
        return bytes.toByteArray();
    }

    private List<String> parseDnsResponse(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.getShort();
        buffer.getShort();
        short questions = buffer.getShort();
        short answers = buffer.getShort();
        buffer.getShort();
        buffer.getShort();
        for (int i = 0; i < questions; i++) {
            skipDomainName(buffer);
            buffer.getShort();
            buffer.getShort();
        }
        List<String> result = new ArrayList<>();
        for (int i = 0; i < answers; i++) {
            skipDomainName(buffer);
            short type = buffer.getShort();
            buffer.getShort();
            buffer.getInt();
            short length = buffer.getShort();
            if (type == 1) {
                StringBuilder sb = new StringBuilder();
                for (int j = 0; j < 4; j++) {
                    sb.append(buffer.get() & 0xFF);
                    if (j < 3) {
                        sb.append('.');
                    }
                }
                result.add(sb.toString());
            } else {
                buffer.position(buffer.position() + length);
            }
        }
        return result;
    }

    private void skipDomainName(ByteBuffer buffer) {
        while (true) {
            byte b = buffer.get();
            int length = b & 0xFF;
            if ((b & 0xC0) == 0xC0) {
                buffer.get();
                return;
            } else if (length == 0) {
                return;
            } else {
                buffer.position(buffer.position() + length);
            }
        }
    }
}
