package com.jieli.opus.ui.opus;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.tabs.TabLayout;
import com.jieli.logcat.JL_Log;
import com.jieli.opus.R;
import com.jieli.opus.data.constant.Constants;
import com.jieli.opus.data.model.opus.OpusConfiguration;
import com.jieli.opus.data.model.opus.OpusParam;
import com.jieli.opus.data.model.opus.OpusResult;
import com.jieli.opus.data.model.resource.ResourceInfo;
import com.jieli.opus.data.model.result.StateResult;
import com.jieli.opus.databinding.FragmentOpusOutputBinding;
import com.jieli.opus.tool.AppUtil;
import com.jieli.opus.tool.ViewKit;
import com.jieli.opus.ui.base.BaseFragment;
import com.jieli.opus.ui.base.SelectFileAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * OPUS输出文件界面
 */
public class OpusOutputFragment extends BaseFragment {

    @NonNull
    private final OpusViewModel mViewModel;
    private FragmentOpusOutputBinding mBinding;
    private SelectFileAdapter mAdapter;

    /**
     * 是否正在读取资源
     */
    private boolean isReadRes;

    public OpusOutputFragment(@NonNull OpusViewModel viewModel) {
        mViewModel = viewModel;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mBinding = FragmentOpusOutputBinding.inflate(inflater, container, false);
        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initUI();
        observerCallback();

        readFileList();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mViewModel.encodeStateMLD.removeObserver(encodeStateObserver);
    }

    private void initUI() {
        mBinding.btnRefresh.setOnClickListener(v -> readFileList());
        mBinding.viewPlayParam.btnAudioPp.setOnClickListener(v -> tryToAudioPlayOrPause());
        mBinding.viewEncodeParam.btnEncode.setOnClickListener(v -> tryToEncode());

        mAdapter = new SelectFileAdapter();
        final View emptyView = LayoutInflater.from(requireContext()).inflate(R.layout.view_empty_tips, null, false);
        TextView tvTips = emptyView.findViewById(R.id.tv_tips);
        tvTips.setText(String.format(Locale.ENGLISH, "%s[%s]", getString(R.string.store_file_tips),
                AppUtil.getOutputFileDirPath(requireContext())));
        emptyView.findViewById(R.id.tv_refresh).setOnClickListener(v -> readFileList());
        mAdapter.setEmptyView(emptyView);
        mAdapter.setOnItemClickListener((adapter, view, position) -> {
            ResourceInfo resource = mAdapter.getItem(position);
            if (null == resource) return;
            if (mAdapter.isSelectedItem(resource)) {
                resource = null;
            }
            mAdapter.updateSelectedItem(resource);
        });
        mBinding.rvPcmFile.setLayoutManager(new LinearLayoutManager(requireContext()));
        mBinding.rvPcmFile.setAdapter(mAdapter);

        mBinding.tlFunction.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int position = tab.getPosition();
                updateTabUI(position);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {

            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {

            }
        });
        mBinding.tlFunction.selectTab(mBinding.tlFunction.getTabAt(0));
        updateTabUI(0);
        updatePlayBtn(false);
    }

    private void observerCallback() {
        mViewModel.syncStateMLD.observe(getViewLifecycleOwner(), result -> {
            if (result.getState() != StateResult.STATE_FINISH) return;
            if (result.isSuccess()) {
                readFileList();
                return;
            }
            showTips(String.format(Locale.ENGLISH, "syncStateMLD: code = %d, %s", result.getCode(), result.getMessage()));
        });
        mViewModel.resourceListMLD.observe(getViewLifecycleOwner(), result -> {
            if (result.getState() != StateResult.STATE_FINISH) return;
            JL_Log.d(tag, "resourceListMLD", "isReadRes = " + isReadRes + ", result " + result.getMessage());
            if (!isReadRes) return;
            if (result.isSuccess()) {
                if (!Constants.DIR_OUTPUT.equals(result.getMessage())) return;
                isReadRes = false;
                List<ResourceInfo> list = new ArrayList<>();
                List<ResourceInfo> src = result.getData();
                if (null != src) {
                    for (ResourceInfo resource : src) {
                        if (resource.getType() == ResourceInfo.TYPE_PCM) {
                            list.add(resource);
                        }
                    }
                }
                mAdapter.setList(list);
                mAdapter.updateSelectedItem(null);
                return;
            }
            isReadRes = false;
            showTips(String.format(Locale.ENGLISH, "resourceListMLD: code = %d, %s", result.getCode(), result.getMessage()));
        });
        mViewModel.playStateMLD.observe(getViewLifecycleOwner(), this::updatePlayBtn);
        mViewModel.encodeStateMLD.observeForever(encodeStateObserver);
    }

    private final Observer<StateResult<OpusResult>> encodeStateObserver = result -> {
        if (result.getState() == StateResult.STATE_WORKING) {
            requireActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            mBinding.viewEncodeParam.btnEncode.setText(getString(R.string.stop_coding));
            OpusResult encodeResult = result.getData();
            if (null != encodeResult && encodeResult.getWay() == OpusParam.WAY_FILE) {
                showLoading(getString(R.string.encoding));
            }
            return;
        }
        if (result.getState() == StateResult.STATE_FINISH) {
            dismissLoading();
            requireActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            mBinding.viewEncodeParam.btnEncode.setText(getString(R.string.start_coding));
            if (!result.isSuccess()) {
                showTips(String.format(Locale.ENGLISH, "encodeStateObserver: code = %d, %s", result.getCode(), result.getMessage()));
            }
        }
    };

    private void updateTabUI(int position) {
        switch (position) {
            case 0: { //play param
                ViewKit.gone(mBinding.viewEncodeParam.getRoot());
                ViewKit.show(mBinding.viewPlayParam.getRoot());
                mBinding.viewPlayParam.spinnerSampleRate.setSelection(1);
                mBinding.viewPlayParam.llChannelNum.setVisibility(Constants.IS_SUPPORT_DUAL_CHANNEL ? View.VISIBLE : View.GONE);
                break;
            }
            case 1: { //encode param
                ViewKit.gone(mBinding.viewPlayParam.getRoot());
                ViewKit.show(mBinding.viewEncodeParam.getRoot());
                mBinding.viewEncodeParam.spinnerSampleRate.setSelection(1);
                //暂不支持自定义编码参数
                mBinding.viewEncodeParam.cbHasHeaders.setEnabled(false);
                mBinding.viewEncodeParam.spinnerSampleRate.setEnabled(false);
                mBinding.viewEncodeParam.etPacket.setEnabled(false);
                //暂不支持双声道编码
                ViewKit.gone(mBinding.viewEncodeParam.llChannelNum);
                break;
            }
        }
    }

    private void updatePlayBtn(boolean isPlaying) {
        if (isPlaying) {
            mBinding.viewPlayParam.btnAudioPp.setText(getString(R.string.audio_pause));
        } else {
            mBinding.viewPlayParam.btnAudioPp.setText(getString(R.string.audio_play));
        }
    }

    public void readFileList() {
        if (mViewModel.isSyncResourceSuccess() && !isReadRes) {
            isReadRes = true;
            mViewModel.readFileList(requireContext(), Constants.DIR_OUTPUT);
        }
    }

    private void tryToAudioPlayOrPause() {
        final ResourceInfo resource = mAdapter.getResource();
        if (null == resource) {
            showTips(getString(R.string.select_file_tips));
            return;
        }
        boolean isPlayAudio = mViewModel.isPlayAudio();
        JL_Log.d(tag, "tryToAudioPlayOrPause", "isPlayAudio : " + isPlayAudio);
        if (isPlayAudio) {
            mViewModel.stopAudioPlay();
            return;
        }
        try {
            int sampleRate = Integer.parseInt((String) mBinding.viewPlayParam.spinnerSampleRate.getSelectedItem());
            int channel = mBinding.viewPlayParam.rgChannelNum.getCheckedRadioButtonId() == R.id.rbtn_dual_channel ? 2 : 1;
            OpusConfiguration option = new OpusConfiguration().setSampleRate(sampleRate).setChannel(channel);
            mViewModel.playAudio(option, resource.getPath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void tryToEncode() {
        if (mViewModel.isEncoding()) {
            mViewModel.stopEncode();
            return;
        }
        final ResourceInfo resource = mAdapter.getResource();
        if (null == resource) {
            showTips(getString(R.string.select_file_tips));
            return;
        }
        try {
            boolean hasHeaders = mBinding.viewEncodeParam.cbHasHeaders.isChecked();
            int packetLen = Integer.parseInt(mBinding.viewEncodeParam.etPacket.getText().toString());
            int sampleRate = Integer.parseInt((String) mBinding.viewEncodeParam.spinnerSampleRate.getSelectedItem());
            int channel = mBinding.viewEncodeParam.rgChannelNum.getCheckedRadioButtonId() == R.id.rbtn_dual_channel ?
                    OpusConfiguration.CHANNEL_DUAL : OpusConfiguration.CHANNEL_MONO;
            int decodeWay = mBinding.viewEncodeParam.rgDecodeWay.getCheckedRadioButtonId() == R.id.rbtn_file_way ?
                    OpusParam.WAY_FILE : OpusParam.WAY_STREAM;
            OpusParam param = new OpusParam(new OpusConfiguration().setHasHead(hasHeaders).setPacketSize(packetLen)
                    .setSampleRate(sampleRate).setChannel(channel))
                    .setWay(decodeWay);
            mViewModel.startEncode(requireContext(), resource.getPath(), param);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}