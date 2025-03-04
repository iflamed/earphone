package com.jieli.opus.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.jieli.component.utils.SystemUtil;
import com.jieli.opus.R;
import com.jieli.opus.databinding.ActivityMainBinding;
import com.jieli.opus.ui.base.BaseActivity;
import com.jieli.opus.ui.opus.OpusFragment;

public class MainActivity extends BaseActivity {

    private ActivityMainBinding mMainBinding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SystemUtil.setImmersiveStateBar(getWindow(), true);
        mMainBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(mMainBinding.getRoot());

        replaceFragment(R.id.fl_content, OpusFragment.class.getCanonicalName(), null);
        findViewById(R.id.btn_bluetooth_spp).setOnClickListener(view -> {
            Intent it = new Intent(view.getContext(), BluetoothSppActivity.class);
            startActivity(it);
        });
    }

    @Override
    public void onBackPressed() {
//        super.onBackPressed();
        finish();
    }
}