package com.jieli.opus.ui.base;

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.jieli.opus.R;
import com.jieli.opus.data.model.resource.ResourceInfo;
import com.jieli.opus.tool.AppUtil;

import java.io.File;
import java.util.Locale;

/**
 * SelectFileAdapter
 *
 * @author zqjasonZhong
 * @email zhongzhuocheng@zh-jieli.com
 * @desc 选择文件适配器
 * @since 2024/5/10
 */
public class SelectFileAdapter extends BaseQuickAdapter<ResourceInfo, BaseViewHolder> {
    /**
     * 选择的资源
     */
    private ResourceInfo resource;

    public SelectFileAdapter() {
        super(R.layout.item_select_file);
    }

    @Override
    protected void convert(@NonNull BaseViewHolder viewHolder, ResourceInfo resourceInfo) {
        if (null == resourceInfo) return;
        File file = new File(resourceInfo.getPath());
        viewHolder.setText(R.id.tv_file_msg, String.format(Locale.ENGLISH, "%s(%s)", resourceInfo.getName(),
                AppUtil.getFileSizeString((int) file.length())));
        viewHolder.setImageResource(R.id.iv_choose_status, isSelectedItem(resourceInfo) ? R.drawable.ic_choose_selected_blue
                : R.drawable.ic_choose_normal_gray);
    }

    public boolean isSelectedItem(ResourceInfo resource) {
        if (null == this.resource || null == resource) return false;
        return this.resource.equals(resource);
    }

    public ResourceInfo getResource() {
        return resource;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void updateSelectedItem(ResourceInfo resource) {
        if (!isSelectedItem(resource)) {
            this.resource = resource;
            notifyDataSetChanged();
        }
    }
}
