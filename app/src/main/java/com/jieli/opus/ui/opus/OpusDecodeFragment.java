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

import com.jieli.opus.R;
import com.jieli.opus.data.constant.Constants;
import com.jieli.opus.data.model.opus.OpusConfiguration;
import com.jieli.opus.data.model.opus.OpusParam;
import com.jieli.opus.data.model.opus.OpusResult;
import com.jieli.opus.data.model.resource.ResourceInfo;
import com.jieli.opus.data.model.result.StateResult;
import com.jieli.opus.databinding.FragmentOpusDecodeBinding;
import com.jieli.opus.tool.AppUtil;
import com.jieli.opus.ui.base.BaseFragment;
import com.jieli.opus.ui.base.SelectFileAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * OPUS解码界面
 */
public class OpusDecodeFragment extends BaseFragment {

    @NonNull
    private final OpusViewModel mViewModel;
    private FragmentOpusDecodeBinding mBinding;
    private SelectFileAdapter mAdapter;

    /**
     * 是否正在读取资源
     */
    private boolean isReadRes;

    public OpusDecodeFragment(@NonNull OpusViewModel viewModel) {
        mViewModel = viewModel;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mBinding = FragmentOpusDecodeBinding.inflate(inflater, container, false);
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
        mViewModel.decodeStateMLD.removeObserver(decodeStateObserver);
    }

    private final Observer<StateResult<OpusResult>> decodeStateObserver = result -> {
        if (result.getState() == StateResult.STATE_WORKING) {
            requireActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            mBinding.btnDecode.setText(getString(R.string.stop_decoding));
            mBinding.btnAudioPp.setBackgroundResource(R.drawable.bg_btn_blue_gray_8_selector);
            OpusResult decodeResult = result.getData();
            if (null != decodeResult && decodeResult.getWay() == OpusParam.WAY_FILE) {
                showLoading(getString(R.string.decoding));
            }
            return;
        }
        if (result.getState() == StateResult.STATE_FINISH) {
            dismissLoading();
            requireActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            mBinding.btnDecode.setText(getString(R.string.start_decoding));
            mBinding.btnAudioPp.setBackgroundResource(R.drawable.bg_btn_8_gray_shape);
            if (!result.isSuccess()) {
                showTips(String.format(Locale.ENGLISH, "decodeStateMLD: code = %d, %s", result.getCode(), result.getMessage()));
            }
        }
    };

    private void initUI() {
        mBinding.btnRefresh.setOnClickListener(v -> readFileList());
        mBinding.btnAudioPp.setOnClickListener(v -> {
            if (!mViewModel.isDecoding()) return;
            if (mViewModel.isPlayAudio()) {
                mViewModel.pause();
            } else {
                mViewModel.play();
            }
        });
        mBinding.btnDecode.setOnClickListener(v -> tryToDecode());

        mBinding.cbHasHeaders.setOnCheckedChangeListener((buttonView, isChecked) -> mBinding.llPacketLen.setVisibility(isChecked ? View.GONE : View.VISIBLE));
        mBinding.spinnerSampleRate.setSelection(1); //默认是16000 Hz

        mAdapter = new SelectFileAdapter();
        final View emptyView = LayoutInflater.from(requireContext()).inflate(R.layout.view_empty_tips, null, false);
        TextView tvTips = emptyView.findViewById(R.id.tv_tips);
        tvTips.setText(String.format(Locale.ENGLISH, "%s[%s]", getString(R.string.store_file_tips),
                AppUtil.getOpusFileDirPath(requireContext())));
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
        mBinding.rvOpusFile.setLayoutManager(new LinearLayoutManager(requireContext()));
        mBinding.rvOpusFile.setAdapter(mAdapter);

        mBinding.llChannelNum.setVisibility(Constants.IS_SUPPORT_DUAL_CHANNEL ? View.VISIBLE : View.GONE);
        updatePlayBtn(false);
        mBinding.btnAudioPp.setBackgroundResource(R.drawable.bg_btn_8_gray_shape);
    }

    private void observerCallback() {
        mViewModel.syncStateMLD.observe(this, result -> {
            if (result.getState() != StateResult.STATE_FINISH) return;
            if (result.isSuccess()) {
                readFileList();
                return;
            }
            showTips(String.format(Locale.ENGLISH, "syncStateMLD: code = %d, %s", result.getCode(), result.getMessage()));
        });
        mViewModel.resourceListMLD.observe(getViewLifecycleOwner(), result -> {
            if (result.getState() != StateResult.STATE_FINISH) return;
            if (!isReadRes) return;
            if (result.isSuccess()) {
                if (!Constants.DIR_OPUS.equals(result.getMessage())) return;
                isReadRes = false;
                List<ResourceInfo> list = new ArrayList<>();
                List<ResourceInfo> src = result.getData();
                if (null != src) {
                    for (ResourceInfo resource : src) {
                        if (resource.getType() == ResourceInfo.TYPE_OPUS) {
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
        mViewModel.decodeStateMLD.observeForever(decodeStateObserver);
        mViewModel.playStateMLD.observe(getViewLifecycleOwner(), this::updatePlayBtn);
    }

    public void readFileList() {
        if (mViewModel.isSyncResourceSuccess() && !isReadRes) {
            isReadRes = true;
            mViewModel.readFileList(requireContext(), Constants.DIR_OPUS);
        }
    }

    private void tryToDecode() {
        if (mViewModel.isDecoding()) {
            mViewModel.stopDecode();
            return;
        }
        final ResourceInfo resource = mAdapter.getResource();
        if (null == resource) {
            showTips(getString(R.string.select_file_tips));
            return;
        }
        try {
            boolean hasHeaders = mBinding.cbHasHeaders.isChecked();
            int packetLen = Integer.parseInt(mBinding.etPacket.getText().toString());
            int sampleRate = Integer.parseInt((String) mBinding.spinnerSampleRate.getSelectedItem());
            int channel = mBinding.rgChannelNum.getCheckedRadioButtonId() == R.id.rbtn_dual_channel ?
                    OpusConfiguration.CHANNEL_DUAL : OpusConfiguration.CHANNEL_MONO;
            int decodeWay = mBinding.rgDecodeWay.getCheckedRadioButtonId() == R.id.rbtn_file_way ?
                    OpusParam.WAY_FILE : OpusParam.WAY_STREAM;
            boolean isPlayAudio = mBinding.cbPlayAudio.isChecked();
            OpusParam param = new OpusParam(new OpusConfiguration().setHasHead(hasHeaders).setPacketSize(packetLen)
                    .setSampleRate(sampleRate).setChannel(channel))
                    .setWay(decodeWay).setPlayAudio(isPlayAudio);
            mViewModel.startDecode(requireContext(), resource.getPath(), param);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updatePlayBtn(boolean isPlaying) {
        if (isPlaying) {
            mBinding.btnAudioPp.setText(getString(R.string.audio_pause));
        } else {
            mBinding.btnAudioPp.setText(getString(R.string.audio_play));
        }
    }
}