package com.jieli.opus.tool;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.annotation.NonNull;

import com.jieli.opus.data.constant.Constants;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;

/**
 * @author zqjasonZhong
 * @email zhongzhuocheng@zh-jieli.com
 * @desc APP工具类
 * @since 2021/7/16
 */
public class AppUtil {

    /**
     * 获取APP的版本名
     *
     * @param context 上下文
     * @return 版本名称
     */
    public static String getVersionName(@NonNull Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return "0.0.0";
    }

    /**
     * 获取APP的版本号
     *
     * @param context 上下文
     * @return 版本号
     */
    public static int getVersionCode(@NonNull Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return 0;
    }

    /**
     * 创建文件路径
     *
     * @param context  上下文
     * @param dirNames 文件夹名
     * @return 路径
     */
    public static String createFilePath(Context context, String... dirNames) {
        if (context == null || dirNames == null || dirNames.length == 0) return null;
        File file = context.getExternalFilesDir(null);
        if (file == null || !file.exists()) return null;
        StringBuilder filePath = new StringBuilder(file.getPath());
        if (filePath.toString().endsWith("/")) {
            filePath = new StringBuilder(filePath.substring(0, filePath.lastIndexOf("/")));
        }
        for (String dirName : dirNames) {
            filePath.append("/").append(dirName);
            file = new File(filePath.toString());
            if (!file.exists() || file.isFile()) {//文件不存在
                if (!file.mkdir()) {
                    Log.w("jieli", "create dir failed. filePath = " + filePath);
                    break;
                }
            }
        }
        return filePath.toString();
    }

    public static boolean deleteFile(File file) {
        if (null == file || !file.exists()) return false;
        if (file.isFile()) return file.delete();
        File[] files = file.listFiles();
        if (null == files) {
            return file.delete();
        }
        for (File subFile : files) {
            if (!deleteFile(subFile)) return false;
        }
        return file.delete();
    }


    /**
     * 复制assets资源
     *
     * @param context 上下文
     * @param oldPath assets路径
     * @param newPath 复制资源路径
     * @return boolean 操作结果
     */
    public static boolean copyAssets(Context context, String oldPath, String newPath) {
        try {
            String[] fileNames = context.getAssets().list(oldPath);// 获取assets目录下的所有文件及目录名
            if (null == fileNames) return false;
            if (fileNames.length > 0) {// 如果是目录
                File file = new File(newPath);
                if (!file.exists()) {
                    boolean ret = file.mkdirs();// 如果文件夹不存在，则递归
                    if (!ret) return false;
                }
                for (String fileName : fileNames) {
                    if (!copyAssets(context, oldPath + File.separator + fileName, newPath + File.separator + fileName)) {
                        return false;
                    }
                }
                return true;
            }
            // 如果是文件
            InputStream is = context.getAssets().open(oldPath);
            FileOutputStream fos = new FileOutputStream(new File(newPath));
            byte[] buffer = new byte[1024];
            int byteCount;
            while ((byteCount = is.read(buffer)) != -1) {// 循环从输入流读取
                // buffer字节
                fos.write(buffer, 0, byteCount);// 将读取的输入流写入到输出流
            }
            fos.flush();// 刷新缓冲区
            is.close();
            fos.close();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public static String getFileSizeString(int fileSize) {
        float value = fileSize / 1024f;
        if (value < 1024) {
            return String.format(Locale.ENGLISH, "%.1f Kb", value);
        }
        value /= 1024f;
        return String.format(Locale.ENGLISH, "%.1f Mb", value);
    }

    public static String getFileDirPath(String filePath) {
        if (null == filePath) return null;
        int index = filePath.lastIndexOf(File.separator);
        if (index == -1) return filePath;
        return filePath.substring(0, index);
    }

    public static String getFileNameByPath(String filePath) {
        if (null == filePath) return null;
        int index = filePath.lastIndexOf(File.separator);
        if (index == -1) return filePath;
        return filePath.substring(index + 1);
    }

    public static String getNameNoSuffix(String name) {
        if (null == name) return null;
        int index = name.lastIndexOf(".");
        if (index == -1) return name;
        return name.substring(0, index);
    }

    public static String getOutputFileDirPath(@NonNull Context context) {
        return createFilePath(context, Constants.DIR_OUTPUT);
    }

    public static String getOpusFileDirPath(@NonNull Context context) {
        return createFilePath(context, Constants.DIR_OPUS);
    }
}
