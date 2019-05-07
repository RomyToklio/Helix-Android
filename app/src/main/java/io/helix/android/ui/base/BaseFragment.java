package io.helix.android.ui.base;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;

import io.helix.android.HelixApplication;
import io.helix.android.module.HelixModule;

/**
 * Created by furszy on 6/29/17.
 */

public class BaseFragment extends Fragment {

    protected HelixApplication HelixApplication;
    protected HelixModule HelixModule;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        HelixApplication = HelixApplication.getInstance();
        HelixModule = HelixApplication.getModule();
    }

    protected boolean checkPermission(String permission) {
        int result = ContextCompat.checkSelfPermission(getActivity(),permission);
        return result == PackageManager.PERMISSION_GRANTED;
    }
}
