package com.jieli.opus;

import android.app.Application;

import com.jieli.component.utils.ToastUtil;
import com.jieli.logcat.JL_Log;
import com.jieli.logcat.data.LogFileOption;
import com.jieli.logcat.data.LogFormat;
import com.jieli.logcat.data.LogLevel;
import com.jieli.logcat.data.LogMode;
import com.jieli.logcat.data.LogOption;

/**
 * MainApplication
 *
 * @author zqjasonZhong
 * @email zhongzhuocheng@zh-jieli.com
 * @desc 主入口
 * @since 2024/5/10
 */
public class MainApplication extends Application {

    public static MainApplication application;

    @Override
    public void onCreate() {
        super.onCreate();
        application = this;

        ToastUtil.init(this);
        JL_Log.configure(new LogOption().setLogMode(LogMode.DEBUG)
                .setLogcatCrash(true)
                .setLogFormat(new LogFormat().setPrintLogDetailLevel(LogLevel.WARN))
                .setSaveFile(true, new LogFileOption(this)));
    }

}
