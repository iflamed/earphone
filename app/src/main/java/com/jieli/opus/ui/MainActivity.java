package com.jieli.opus.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

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
        Button btnBluetoothTest = findViewById(R.id.btnBluetoothTest);
        btnBluetoothTest.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, BluetoothTestActivity.class);
            startActivity(intent);
        });
    }

    @Override
    public void onBackPressed() {
//        super.onBackPressed();
        finish();
    }
}