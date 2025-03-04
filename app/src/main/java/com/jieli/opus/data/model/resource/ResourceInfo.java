package com.jieli.opus.data.model.resource;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * @author zqjasonZhong
 * @email zhongzhuocheng@zh-jieli.com
 * @desc 资源信息
 * @since 2024/1/23
 */
public class ResourceInfo {
    /**
     * 未知文件
     */
    public static final int TYPE_UNKNOWN = 0;
    /**
     * PNG文件
     */
    public static final int TYPE_PNG = 1;
    /**
     * JPEG文件
     */
    public static final int TYPE_JPEG = 2;
    /**
     * GIF文件
     */
    public static final int TYPE_GIF = 3;
    /**
     * OPUS文件
     */
    public static final int TYPE_OPUS = 4;
    /**
     * SPEEX文件
     */
    public static final int TYPE_SPEEX = 5;
    /**
     * PCM文件
     */
    public static final int TYPE_PCM = 6;


    /**
     * 资源名称
     */
    @NonNull
    private final String name;
    /**
     * 资源类型
     */
    private final int type;
    /**
     * 文件路径
     */
    @NonNull
    private final String path;

    public ResourceInfo(@NonNull String name, @NonNull String path) {
        this(name, getFileType(name), path);
    }

    public ResourceInfo(@NonNull String name, int type, @NonNull String path) {
        this.name = name;
        this.type = type;
        this.path = path;
    }

    @NonNull
    public String getName() {
        return name;
    }

    public int getType() {
        return type;
    }

    @NonNull
    public String getPath() {
        return path;
    }

    @Override
    public String toString() {
        return "ResourceInfo{" +
                "name='" + name + '\'' +
                ", type=" + type +
                ", path='" + path + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ResourceInfo that = (ResourceInfo) o;
        return path.equals(that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path);
    }

    public static int getFileType(String fileName) {
        if (null == fileName || fileName.length() == 0) return ResourceInfo.TYPE_UNKNOWN;
        int index = fileName.lastIndexOf(".");
        if (index == -1) return ResourceInfo.TYPE_UNKNOWN;
        String suffix = fileName.substring(index + 1);
        if ("png".equalsIgnoreCase(suffix)) {
            return ResourceInfo.TYPE_PNG;
        } else if ("jpeg".equalsIgnoreCase(suffix) || "jpg".equalsIgnoreCase(suffix)) {
            return ResourceInfo.TYPE_JPEG;
        } else if ("gif".equalsIgnoreCase(suffix)) {
            return ResourceInfo.TYPE_GIF;
        } else if ("opus".equalsIgnoreCase(suffix)) {
            return ResourceInfo.TYPE_OPUS;
        } else if ("speex".equalsIgnoreCase(suffix)) {
            return ResourceInfo.TYPE_SPEEX;
        } else if ("pcm".equalsIgnoreCase(suffix)) {
            return ResourceInfo.TYPE_PCM;
        } else {
            return ResourceInfo.TYPE_UNKNOWN;
        }
    }
}
