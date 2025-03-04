package com.jieli.opus.tool;

import android.view.View;

/**
 * @author zqjasonZhong
 * @email zhongzhuocheng@zh-jieli.com
 * @desc 控件工具类
 * @since 2024/1/23
 */
public class ViewKit {

    public static void setViewVisibility(View view, int visibility) {
        if (null == view) return;
        if (visibility != view.getVisibility()) {
            view.setVisibility(visibility);
        }
    }

    public static void show(View view) {
        setViewVisibility(view, View.VISIBLE);
    }

    public static void hide(View view) {
        setViewVisibility(view, View.INVISIBLE);
    }

    public static void gone(View view) {
        setViewVisibility(view, View.GONE);
    }

    public static boolean isShow(View view) {
        return view != null && view.getVisibility() == View.VISIBLE;
    }

    public static boolean isHide(View view) {
        return view != null && view.getVisibility() == View.INVISIBLE;
    }

    public static boolean isGone(View view) {
        return view != null && view.getVisibility() == View.GONE;
    }
}
