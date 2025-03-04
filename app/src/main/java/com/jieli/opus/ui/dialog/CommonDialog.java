package com.jieli.opus.ui.dialog;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.FloatRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.jieli.opus.ui.base.BaseDialogFragment;

/**
 * @author zqjasonZhong
 * @email zhongzhuocheng@zh-jieli.com
 * @desc 通用对话框
 * @since 2024/1/24
 */
public abstract class CommonDialog extends BaseDialogFragment {
    protected final Builder mBuilder;

    public CommonDialog(Builder builder) {
        mBuilder = builder;
    }

    @Override
    public void onStart() {
        super.onStart();
        final Window window = requireDialog().getWindow();
        if (null == window) return;
        WindowManager.LayoutParams layoutParams = window.getAttributes();
        layoutParams.dimAmount = mBuilder.dimAmount;
        layoutParams.gravity = mBuilder.gravity;
        layoutParams.flags = layoutParams.flags | WindowManager.LayoutParams.FLAG_DIM_BEHIND;

        layoutParams.width = mBuilder.widthRate == 0f ? WindowManager.LayoutParams.WRAP_CONTENT :
                (int) (mBuilder.widthRate * getScreenWidth());
        layoutParams.height = mBuilder.heightRate == 0f ? WindowManager.LayoutParams.WRAP_CONTENT :
                (int) (mBuilder.heightRate * getScreenHeight());

        if (mBuilder.x != 0) layoutParams.x = mBuilder.x;
        if (mBuilder.y != 0) layoutParams.y = mBuilder.y;

        window.setAttributes(layoutParams);
        int color = mBuilder.backgroundColor == 0 ? Color.TRANSPARENT : ContextCompat.getColor(requireContext(), mBuilder.backgroundColor);
        window.setBackgroundDrawable(new ColorDrawable(color));
        View root = window.getDecorView();
        if (null == root) return;
        View rootView = root.getRootView();
        if (null == rootView) return;
        rootView.setBackgroundColor(color);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return createView(inflater, container, savedInstanceState);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        requireDialog().setCanceledOnTouchOutside(mBuilder.cancelable);
        setCancelable(mBuilder.cancelable);
    }

    public Builder getBuilder() {
        return mBuilder;
    }

    public abstract View createView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState);

    public interface OnViewClick {

        void onClick(CommonDialog dialog, View view);
    }

    public static class TextStyle {
        private String text;
        private int color = 0;
        private int size = 0;
        private boolean isBold = false;
        private int gravity = Gravity.CENTER;
        private OnViewClick onClick;
        private int topDrawableResID = 0;

        public String getText() {
            return text;
        }

        public TextStyle setText(String text) {
            this.text = text;
            return this;
        }

        public int getColor() {
            return color;
        }

        public TextStyle setColor(int color) {
            this.color = color;
            return this;
        }

        public int getSize() {
            return size;
        }

        public TextStyle setSize(int size) {
            this.size = size;
            return this;
        }

        public boolean isBold() {
            return isBold;
        }

        public TextStyle setBold(boolean bold) {
            isBold = bold;
            return this;
        }

        public int getGravity() {
            return gravity;
        }

        public TextStyle setGravity(int gravity) {
            this.gravity = gravity;
            return this;
        }

        public OnViewClick getOnClick() {
            return onClick;
        }

        public TextStyle setOnClick(OnViewClick onClick) {
            this.onClick = onClick;
            return this;
        }

        public int getTopDrawableResID() {
            return topDrawableResID;
        }

        public TextStyle setTopDrawableResID(int topDrawableResID) {
            this.topDrawableResID = topDrawableResID;
            return this;
        }
    }

    public static class ButtonStyle extends TextStyle {

    }

    public abstract static class Builder {
        private int gravity = Gravity.CENTER;
        @FloatRange(from = 0.0, to = 1.0)
        private float widthRate = 0.9f;
        @FloatRange(from = 0.0, to = 1.0)
        private float heightRate = 0f;
        private boolean cancelable = true;
        private int x = 0;
        private int y = 0;
        private int backgroundColor = 0;
        @FloatRange(from = 0.0, to = 1.0)
        private float dimAmount = 0.5f;

        public int getGravity() {
            return gravity;
        }

        public Builder setGravity(int gravity) {
            this.gravity = gravity;
            return this;
        }

        public float getWidthRate() {
            return widthRate;
        }

        public Builder setWidthRate(float widthRate) {
            this.widthRate = widthRate;
            return this;
        }

        public float getHeightRate() {
            return heightRate;
        }

        public Builder setHeightRate(float heightRate) {
            this.heightRate = heightRate;
            return this;
        }

        public boolean isCancelable() {
            return cancelable;
        }

        public Builder setCancelable(boolean cancelable) {
            this.cancelable = cancelable;
            return this;
        }

        public int getX() {
            return x;
        }

        public Builder setX(int x) {
            this.x = x;
            return this;
        }

        public int getY() {
            return y;
        }

        public Builder setY(int y) {
            this.y = y;
            return this;
        }

        public int getBackgroundColor() {
            return backgroundColor;
        }

        public Builder setBackgroundColor(int backgroundColor) {
            this.backgroundColor = backgroundColor;
            return this;
        }

        public float getDimAmount() {
            return dimAmount;
        }

        public Builder setDimAmount(float dimAmount) {
            this.dimAmount = dimAmount;
            return this;
        }

        public abstract CommonDialog build();
    }
}
