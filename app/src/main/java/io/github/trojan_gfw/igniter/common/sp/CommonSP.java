package io.github.trojan_gfw.igniter.common.sp;

import android.content.Context;
import android.content.SharedPreferences;

public class CommonSP {
    private static final String SP_NAME = "common_sp";
    private static final String KEY_SERVER_SUBSCRIBE_URL = "server_sub_url";
    private static final String KEY_EXTRA_DNS = "EXTRA_DNS";
    private static Context sApplicationContext;

    public static void init(Context context) {
        // 使用 ApplicationContext 避免内存泄漏
        sApplicationContext = context.getApplicationContext();
    }

    private static SharedPreferences sp() {
        return sApplicationContext.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
    }

    private static SharedPreferences.Editor edit() {
        return sp().edit();
    }

    public static String getServerSubscribeUrl(String defaultVal) {
        return sp().getString(KEY_SERVER_SUBSCRIBE_URL, defaultVal);
    }

    public static void setServerSubscribeUrl(String url) {
        edit().putString(KEY_SERVER_SUBSCRIBE_URL, url).apply();
    }

    public static String getExtraDNSJSONArrayString(String defaultValue) {
        return sp().getString(KEY_EXTRA_DNS, defaultValue);
    }

    public static void setExtraDNSJSONArrayString(String jsonArrayString) {
        edit().putString(KEY_EXTRA_DNS, jsonArrayString).apply();
    }
}
