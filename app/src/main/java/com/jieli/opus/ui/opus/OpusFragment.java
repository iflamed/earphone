package com.jieli.opus.ui.opus;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.jieli.logcat.JL_Log;
import com.jieli.opus.R;
import com.jieli.opus.data.model.opus.OpusResult;
import com.jieli.opus.data.model.result.StateResult;
import com.jieli.opus.databinding.FragmentOpusBinding;
import com.jieli.opus.tool.AppUtil;
import com.jieli.opus.ui.base.BaseFragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * OPUS测试界面
 */
public class OpusFragment extends BaseFragment {

    private static final boolean IS_ALLOW_SCROLL = false;

    private FragmentOpusBinding mBinding;
    private OpusViewModel mViewModel;

    /**
     * Fragment集合
     */
    private final List<BaseFragment> fragments = new ArrayList<>();
    /**
     * 选中的Tab位置
     */
    private int selectedPos = 0;

    private final Observer<StateResult<OpusResult>> decodeObserver = result -> {
        if (result.getState() != StateResult.STATE_FINISH) return;
        if (!result.isSuccess()) {
            showResultDialog(getString(R.string.decoding_fail), result.getMessage(), null);
        }
    };

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mBinding = FragmentOpusBinding.inflate(inflater, container, false);
        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mViewModel = new ViewModelProvider(requireActivity()).get(OpusViewModel.class);

        initUI();
        observeCallback();
        mViewModel.syncResource(requireContext());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mViewModel.decodeStateMLD.removeObserver(decodeObserver);
    }

    private void initUI() {
        mBinding.viewTopBar.tvLeft.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
        String appVersion = String.format(Locale.ENGLISH, "%s", AppUtil.getVersionName(requireContext()));
        mBinding.viewTopBar.tvLeft.setText(appVersion);
        mBinding.viewTopBar.tvTitle.setText(getString(R.string.opus_decoding));

        mBinding.tlFunction.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int position = tab.getPosition();
                boolean isDecoding = mViewModel.isDecoding();
                JL_Log.d(tag, "onTabSelected", "isDecoding : " + isDecoding);
                if (isDecoding) {
                    if (position != selectedPos) {
                        mBinding.tlFunction.selectTab(mBinding.tlFunction.getTabAt(selectedPos));
                        mBinding.vp2Function.setCurrentItem(selectedPos);
                    }
                    return;
                }
                selectedPos = position;
                mBinding.vp2Function.setCurrentItem(position, true);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {

            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {

            }
        });
        fragments.add(new OpusDecodeFragment(mViewModel));
        fragments.add(new OpusOutputFragment(mViewModel));

        mBinding.vp2Function.setAdapter(new MyAdapter(getChildFragmentManager(), getLifecycle(), fragments));
        if (!IS_ALLOW_SCROLL) mBinding.vp2Function.setUserInputEnabled(false);
        mBinding.vp2Function.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {

            @Override
            public void onPageSelected(int position) {
                if (IS_ALLOW_SCROLL) {
                    mBinding.tlFunction.selectTab(mBinding.tlFunction.getTabAt(position));
                }
            }

        });
    }

    private void observeCallback() {
        mViewModel.decodeStateMLD.observeForever(decodeObserver);
    }

    private static class MyAdapter extends FragmentStateAdapter {
        private final List<BaseFragment> fragments;

        public MyAdapter(@NonNull FragmentManager fragmentManager, @NonNull Lifecycle lifecycle, @NonNull List<BaseFragment> list) {
            super(fragmentManager, lifecycle);
            fragments = list;
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return fragments.get(position);
        }

        @Override
        public int getItemCount() {
            return fragments.size();
        }
    }
}