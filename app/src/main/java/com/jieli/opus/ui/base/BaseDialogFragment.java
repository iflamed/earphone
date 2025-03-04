package com.jieli.opus.ui.base;

import android.content.DialogInterface;
import android.util.DisplayMetrics;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.jieli.component.utils.ToastUtil;
import com.jieli.logcat.JL_Log;

/**
 * @author zqjasonZhong
 * @email zhongzhuocheng@zh-jieli.com
 * @desc DialogFragment基类
 * @since 2024/1/24
 */
public class BaseDialogFragment extends DialogFragment {
    protected final String tag = getClass().getSimpleName();
    private boolean isShow;

    @Override
    public void show(@NonNull FragmentManager manager, @Nullable String tag) {
//        super.show(manager, tag);
        isShow = true;
        FragmentTransaction transaction = manager.beginTransaction();
        transaction.add(this, tag);
        transaction.commitAllowingStateLoss();
    }

    @Override
    public void onResume() {
        isShow = true;
        super.onResume();
    }

    @Override
    public void dismiss() {
//        super.dismiss();
        isShow = false;
        super.dismissAllowingStateLoss();
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        isShow = false;
        super.onDismiss(dialog);
    }

    @Override
    public void onDestroyView() {
        isShow = false;
        super.onDestroyView();
    }

    public boolean isShow() {
        return isShow;
    }

    public int getScreenWidth() {
        final DisplayMetrics displayMetrics = getDisplayMetrics();
        if (null == displayMetrics) return 0;
        return displayMetrics.widthPixels;
    }

    public int getScreenHeight() {
        final DisplayMetrics displayMetrics = getDisplayMetrics();
        if (null == displayMetrics) return 0;
        return displayMetrics.heightPixels;
    }

    protected void showTips(String tips) {
        ToastUtil.showToastLong(tips);
        JL_Log.i(tag, tips);
    }

    private DisplayMetrics getDisplayMetrics() {
        return requireContext().getResources().getDisplayMetrics();
    }
}
