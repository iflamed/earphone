package com.jieli.opus.ui.dialog;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.jieli.opus.R;
import com.jieli.opus.databinding.DialogTipsBinding;
import com.jieli.opus.tool.ViewKit;

/**
 * @author zqjasonZhong
 * @email zhongzhuocheng@zh-jieli.com
 * @desc 提示框
 * @since 2024/3/21
 */
public class TipsDialog extends CommonDialog {
    private DialogTipsBinding mBinding;

    private TipsDialog(Builder builder) {
        super(builder);
    }

    @Override
    public View createView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mBinding = DialogTipsBinding.inflate(inflater, container, false);
        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        refreshUI();
    }

    public void refreshUI() {
        if (!(mBuilder instanceof Builder)) return;
        Builder builder = (Builder) mBuilder;
        //更新Title
        updateTextView(mBinding.tvTitle, builder.titleStyle);
        //更新Content
        updateTextView(mBinding.tvContent, builder.contentStyle);
        //更新取消按钮
        updateButton(mBinding.btnCancel, getString(R.string.cancel), builder.cancelBtnStyle);
        //更新确认按钮
        updateButton(mBinding.btnSure, getString(R.string.confirm), builder.sureBtnStyle);
        //更新分隔线
        updateLine(builder);
    }

    private void updateTextView(@NonNull TextView textView, TextStyle textStyle) {
        boolean isShowView = null != textStyle;
        textView.setVisibility(isShowView ? View.VISIBLE : View.GONE);
        if (!isShowView) return;
        textView.setText(textStyle.getText());
        int color = textStyle.getColor();
        textView.setTextColor(ContextCompat.getColor(requireContext(), color == 0 ? R.color.black : color));
        textView.setTextSize(textStyle.getSize() == 0 ? 18 : textStyle.getSize());
        textView.setTypeface(textStyle.isBold() ? Typeface.DEFAULT_BOLD : Typeface.defaultFromStyle(Typeface.NORMAL));
        textView.setGravity(textStyle.getGravity());
        final OnViewClick click = textStyle.getOnClick();
        if (null != click) {
            textView.setOnClickListener(v -> click.onClick(TipsDialog.this, v));
        }
        textView.setCompoundDrawablesRelativeWithIntrinsicBounds(0, textStyle.getTopDrawableResID(), 0, 0);
    }

    private void updateButton(@NonNull Button button, String defaultText, ButtonStyle buttonStyle) {
        boolean isShowView = null != buttonStyle;
        button.setVisibility(isShowView ? View.VISIBLE : View.GONE);
        if (!isShowView) return;
        button.setText(TextUtils.isEmpty(buttonStyle.getText()) ? defaultText : buttonStyle.getText());
        int color = buttonStyle.getColor();
        button.setTextColor(ContextCompat.getColor(requireContext(), color == 0 ? R.color.blue_204DFD : color));
        button.setTextSize(buttonStyle.getSize() == 0 ? 18 : buttonStyle.getSize());
        button.setTypeface(buttonStyle.isBold() ? Typeface.DEFAULT_BOLD : Typeface.defaultFromStyle(Typeface.NORMAL));
        button.setGravity(buttonStyle.getGravity());
        button.setOnClickListener(v -> {
            dismiss();
            final OnViewClick click = buttonStyle.getOnClick();
            if (null != click) {
                click.onClick(TipsDialog.this, v);
            }
        });
        //button.setCompoundDrawablesRelativeWithIntrinsicBounds(0, buttonStyle.getTopDrawableResID(), 0, 0);
    }

    private void updateLine(@NonNull Builder builder) {
        if (builder.cancelBtnStyle != null && builder.sureBtnStyle != null) {
            ViewKit.show(mBinding.viewHorizontalLine);
            ViewKit.show(mBinding.viewLine);
        } else if (builder.cancelBtnStyle == null && builder.sureBtnStyle == null) {
            ViewKit.gone(mBinding.viewHorizontalLine);
            ViewKit.gone(mBinding.viewLine);
        } else {
            ViewKit.gone(mBinding.viewLine);
            ViewKit.show(mBinding.viewHorizontalLine);
        }
    }

    public static class Builder extends CommonDialog.Builder {
        private TextStyle titleStyle;
        private TextStyle contentStyle;

        private ButtonStyle cancelBtnStyle;
        private ButtonStyle sureBtnStyle;

        private Object data;

        public Builder() {
            setWidthRate(0.8f);
        }

        public Builder title(String text) {
            if (null == titleStyle) {
                titleStyle = new TextStyle().setText(text).setBold(true);
            } else {
                titleStyle.setText(text);
            }
            return this;
        }

        public Builder content(String text) {
            return content(text, false);
        }

        public Builder content(String text, boolean isBold) {
            return content(text, isBold, 0);
        }

        public Builder content(String text, boolean isBold, int topDrawableRes) {
            if (null == contentStyle) {
                contentStyle = new TextStyle();
            }
            contentStyle.setText(text).setBold(isBold).setTopDrawableResID(topDrawableRes);
            return this;
        }

        public Builder cancelBtn(OnViewClick click) {
            return cancelBtn("", click);
        }

        public Builder cancelBtn(String text, OnViewClick click) {
            return cancelBtn(text, 0, click);
        }

        public Builder cancelBtn(String text, int color, OnViewClick click) {
            return cancelBtn(text, color, false, click);
        }

        public Builder cancelBtn(String text, int color, boolean isBold, OnViewClick click) {
            if (null == cancelBtnStyle) {
                cancelBtnStyle = new ButtonStyle();
            }
            cancelBtnStyle.setText(text).setColor(color).setBold(isBold).setOnClick(click);
            return this;
        }

        public Builder confirmBtn(OnViewClick click) {
            return confirmBtn("", click);
        }

        public Builder confirmBtn(String text, OnViewClick click) {
            return confirmBtn(text, 0, click);
        }

        public Builder confirmBtn(String text, int color, OnViewClick click) {
            return confirmBtn(text, color, false, click);
        }

        public Builder confirmBtn(String text, int color, boolean isBold, OnViewClick click) {
            if (null == sureBtnStyle) {
                sureBtnStyle = new ButtonStyle();
            }
            sureBtnStyle.setText(text).setColor(color).setBold(isBold).setOnClick(click);
            return this;
        }

        public Builder extraData(Object data) {
            this.data = data;
            return this;
        }

        public Object getData() {
            return data;
        }

        @Override
        public TipsDialog build() {
            return new TipsDialog(this);
        }
    }
}
