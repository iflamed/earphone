package com.jieli.opus.ui.dialog;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.jieli.opus.R;
import com.jieli.opus.databinding.DialogLoadingBinding;
import com.jieli.opus.tool.ViewKit;

/**
 * @author zqjasonZhong
 * @email zhongzhuocheng@zh-jieli.com
 * @desc 等待框
 * @since 2024/1/24
 */
public class LoadingDialog extends CommonDialog {

    private DialogLoadingBinding mBinding;

    private LoadingDialog(Builder builder) {
        super(builder);
    }

    @Override
    public View createView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mBinding = DialogLoadingBinding.inflate(inflater, container, false);
        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        updateUI();
    }


    public void updateUI() {
        if (!isAdded() || isDetached()) return;
        if (!(mBuilder instanceof Builder)) return;
        Builder builder = (Builder) mBuilder;
        if (builder.loadingColor != 0) {
            mBinding.aivLoading.setIndicatorColor(builder.loadingColor);
        }
        mBinding.aivLoading.show();
        if (builder.textStyle == null) {
            ViewKit.gone(mBinding.tvText);
        } else {
            ViewKit.show(mBinding.tvText);
            TextStyle textStyle = builder.textStyle;
            mBinding.tvText.setText(textStyle.getText());
            mBinding.tvText.setTextColor(ContextCompat.getColor(requireContext(), textStyle.getColor() == 0 ? R.color.white
                    : textStyle.getColor()));
            mBinding.tvText.setTextSize(textStyle.getSize() == 0 ? 14 : textStyle.getSize());
            mBinding.tvText.setTypeface(textStyle.isBold() ? Typeface.DEFAULT_BOLD : Typeface.defaultFromStyle(Typeface.NORMAL));
            mBinding.tvText.setGravity(textStyle.getGravity());
            OnViewClick viewClick = textStyle.getOnClick();
            if (null != viewClick) {
                mBinding.tvText.setOnClickListener(v -> {
                    viewClick.onClick(LoadingDialog.this, v);
                });
            }
        }
    }

    public static class Builder extends CommonDialog.Builder {
        private boolean isChangeWidth;
        private int loadingColor = 0;
        private TextStyle textStyle;

        public boolean isChangeWidth() {
            return isChangeWidth;
        }

        public Builder setChangeWidth(boolean changeWidth) {
            isChangeWidth = changeWidth;
            return this;
        }

        public int getLoadingColor() {
            return loadingColor;
        }

        public Builder setLoadingColor(int loadingColor) {
            this.loadingColor = loadingColor;
            return this;
        }

        public TextStyle getTextStyle() {
            return textStyle;
        }

        public Builder setText(String text) {
            if (null == textStyle) {
                textStyle = new TextStyle();
            }
            textStyle.setText(text);
            return this;
        }

        @Override
        public LoadingDialog build() {
            if (!isChangeWidth) setWidthRate(0f);
            return new LoadingDialog(this);
        }
    }
}
