package com.jieli.opus.ui.base;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.jieli.logcat.JL_Log;
import com.jieli.opus.R;
import com.jieli.opus.data.constant.Constants;
import com.jieli.opus.data.model.resource.ResourceInfo;
import com.jieli.opus.data.model.result.StateResult;
import com.jieli.opus.tool.AppUtil;
import com.jieli.opus.tool.ConfigurationKit;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ResourceViewModel
 *
 * @author zqjasonZhong
 * @email zhongzhuocheng@zh-jieli.com
 * @desc 资源管理逻辑实现
 * @since 2024/5/10
 */
public class ResourceViewModel extends ViewModel {
    protected final String tag = getClass().getSimpleName();
    public final MutableLiveData<StateResult<Boolean>> syncStateMLD = new MutableLiveData<>();
    public final MutableLiveData<StateResult<List<ResourceInfo>>> resourceListMLD = new MutableLiveData<>();

    protected final ConfigurationKit mConfigurationKit = ConfigurationKit.getInstance();
    protected final ExecutorService threadPool = Executors.newSingleThreadExecutor();

    @Override
    protected void onCleared() {
        super.onCleared();
        if (!threadPool.isShutdown()) {
            threadPool.shutdownNow();
        }
    }

    public boolean isSyncResourceSuccess() {
        StateResult<Boolean> result = syncStateMLD.getValue();
        if (null == result) return false;
        return result.getState() == StateResult.STATE_FINISH && result.isSuccess();
    }

    public void syncResource(@NonNull Context context) {
        if (!mConfigurationKit.isNeedUpdateResource()) {
            syncStateMLD.postValue((StateResult<Boolean>) new StateResult<Boolean>().setState(StateResult.STATE_FINISH).setCode(0).setData(true));
            return;
        }
        if (syncStateMLD.getValue() != null && syncStateMLD.getValue().getState() == StateResult.STATE_WORKING) {
            JL_Log.d(tag, "syncResource", "Resource is loading.");
            return;
        }
        syncStateMLD.postValue((StateResult<Boolean>) new StateResult<Boolean>().setState(StateResult.STATE_WORKING).setCode(0));
        threadPool.submit(() -> {
            String[] dirArray = new String[]{Constants.DIR_OPUS};
            boolean ret = false;
            for (String dirName : dirArray) {
                File dirFile = new File(AppUtil.createFilePath(context, dirName));
                if (dirFile.exists()) {
                    AppUtil.deleteFile(dirFile);
                    dirFile.mkdirs();
                }
                ret = AppUtil.copyAssets(context, dirName, dirFile.getPath());
                if (!ret) break;
            }
            if (ret) {
                mConfigurationKit.updateResourceVersion();
            }
            syncStateMLD.postValue((StateResult<Boolean>) new StateResult<Boolean>().setState(StateResult.STATE_FINISH)
                    .setCode(ret ? 0 : -1)
                    .setMessage(ret ? context.getString(R.string.sync_resource_success) : context.getString(R.string.sync_resource_failed)));
        });
    }

    public void readFileList(@NonNull Context context, String dirName) {
        if (resourceListMLD.getValue() != null && resourceListMLD.getValue().getState() == StateResult.STATE_WORKING) {
            JL_Log.d(tag, "readFileList", "Reading resource.");
            return;
        }
        resourceListMLD.postValue((StateResult<List<ResourceInfo>>) new StateResult<List<ResourceInfo>>().setState(StateResult.STATE_WORKING).setCode(0));
        threadPool.submit(() -> {
            File dirFile = new File(AppUtil.createFilePath(context, dirName));
            if (!dirFile.exists() || dirFile.isFile()) {
                resourceListMLD.postValue((StateResult<List<ResourceInfo>>) new StateResult<List<ResourceInfo>>().setState(StateResult.STATE_FINISH)
                        .setCode(10)
                        .setMessage("File Not Found."));
                return;
            }
            File[] files = dirFile.listFiles();
            if (null == files || files.length == 0) {
                resourceListMLD.postValue((StateResult<List<ResourceInfo>>) new StateResult<List<ResourceInfo>>().setState(StateResult.STATE_FINISH)
                        .setCode(11)
                        .setMessage("None File."));
                return;
            }
            List<ResourceInfo> list = new ArrayList<>();
            for (File file : files) {
                JL_Log.d(tag, "readFileList", "name : " + file.getName());
                list.add(new ResourceInfo(file.getName(), file.getPath()));
            }
            resourceListMLD.postValue((StateResult<List<ResourceInfo>>) new StateResult<List<ResourceInfo>>().setState(StateResult.STATE_FINISH)
                    .setCode(0)
                    .setMessage(dirFile.getName())
                    .setData(list));
        });
    }
}
