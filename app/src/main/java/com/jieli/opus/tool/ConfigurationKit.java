package com.jieli.opus.tool;

import android.content.Context;
import android.content.SharedPreferences;

import com.jieli.opus.MainApplication;

/**
 * @author zqjasonZhong
 * @email zhongzhuocheng@zh-jieli.com
 * @desc 配置工具
 * @since 2024/1/22
 */
public class ConfigurationKit {
    private static final ConfigurationKit ourInstance = new ConfigurationKit();

    private static final String KEY_RESOURCE_VERSION = "resource_version";

    public static ConfigurationKit getInstance() {
        return ourInstance;
    }

    private final SharedPreferences sp = MainApplication.application.getSharedPreferences("convert_save.sp", Context.MODE_PRIVATE);

    private ConfigurationKit() {
    }

    public boolean isNeedUpdateResource() {
        int version = sp.getInt(KEY_RESOURCE_VERSION, 0);
        if (version == 0) return true;
        return version < AppUtil.getVersionCode(MainApplication.application);
    }

    public void updateResourceVersion() {
        int currentVersion = AppUtil.getVersionCode(MainApplication.application);
        sp.edit().putInt(KEY_RESOURCE_VERSION, currentVersion).apply();
    }
}
