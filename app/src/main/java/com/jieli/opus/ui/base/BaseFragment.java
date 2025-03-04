package com.jieli.opus.ui.base;

import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.jieli.component.utils.ToastUtil;
import com.jieli.logcat.JL_Log;
import com.jieli.opus.ui.dialog.CommonDialog;
import com.jieli.opus.ui.dialog.LoadingDialog;
import com.jieli.opus.ui.dialog.TipsDialog;

/**
 * @author zqjasonZhong
 * @email zhongzhuocheng@zh-jieli.com
 * @desc Fragment基类
 * @since 2024/1/22
 */
public class BaseFragment extends Fragment {
    protected final String tag = getClass().getSimpleName();

    protected LoadingDialog mLoadingDialog;

    protected boolean isValid() {
        return !isDetached() && isAdded();
    }

    public void replaceFragment(int containerId, String fragmentName, Bundle bundle) {
        replaceFragment(containerId, fragmentName, bundle, false);
    }

    public void replaceFragment(int containerId, String fragmentName, Bundle bundle, boolean isReplace) {
        if (TextUtils.isEmpty(fragmentName)) return;
        Fragment fragment = getChildFragmentManager().findFragmentByTag(fragmentName);
        if (null == fragment) {
            try {
                fragment = (Fragment) Class.forName(fragmentName).newInstance();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (null == fragment) return;
        FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
        if (null != bundle) {
            fragment.setArguments(bundle);
        }
        if (isReplace) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                for (Fragment f : getChildFragmentManager().getFragments()) {
                    transaction.remove(f);
                }
            }
            transaction.replace(containerId, fragment);
        } else {
            for (Fragment f : getChildFragmentManager().getFragments()) {
                transaction.hide(f);
            }
            if (!fragment.isAdded()) {
                transaction.add(containerId, fragment, fragmentName);
                transaction.addToBackStack(fragmentName);
            }
            transaction.show(fragment);
        }
        transaction.commitAllowingStateLoss();
    }

    protected void showTips(String tips) {
        ToastUtil.showToastLong(tips);
        JL_Log.i(tag, tips);
    }

    protected void showLoading(String content) {
        if (!isValid()) return;
        if (mLoadingDialog != null) dismissLoading();
        mLoadingDialog = (LoadingDialog) new LoadingDialog.Builder().setText(content).setCancelable(false).build();
        mLoadingDialog.show(getChildFragmentManager(), LoadingDialog.class.getSimpleName());
    }

    protected void dismissLoading() {
        if (null != mLoadingDialog) {
            if (mLoadingDialog.isShow() && isValid()) {
                mLoadingDialog.dismiss();
            }
            mLoadingDialog = null;
        }
    }

    protected void showResultDialog(String title, String tips, CommonDialog.OnViewClick sureClick) {
        if (!isValid()) return;
        new TipsDialog.Builder().title(title).content(tips)
                .confirmBtn(sureClick).setCancelable(false).build()
                .show(getChildFragmentManager(), TipsDialog.class.getSimpleName());
    }
}
