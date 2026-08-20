package io.github.mpstudios56.cifra.activity;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.Toast;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.Extra;
import org.androidannotations.annotations.ViewById;

import io.github.mpstudios56.cifra.R;
import io.github.mpstudios56.cifra.utils.MyPreferences;

@EActivity(R.layout.activity_request_permissions)
public class RequestPermissionActivity extends Activity {

    @Extra("requestedPermission")
    String requestedPermission;

    @ViewById(R.id.toggleCameraWrap)
    ViewGroup toggleCameraWrap;

    @ViewById(R.id.toggleCamera)
    SwitchCompat toggleCamera;

    @ViewById(R.id.toggleSmsWrap)
    ViewGroup toggleSmsWrap;

    @ViewById(R.id.toggleSms)
    SwitchCompat toggleSms;

    @ViewById(R.id.toggleNotificationWrap)
    ViewGroup toggleNotificationWrap;

    @ViewById(R.id.toggleNotification)
    SwitchCompat toggleNotification;

    @ViewById(R.id.toggleNotificationListenerWrap)
    ViewGroup toggleNotificationListenerWrap;

    @ViewById(R.id.toggleNotificationListener)
    SwitchCompat toggleNotificationListener;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(MyPreferences.switchLocale(base));
    }

    @AfterViews
    public void initViews() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.scroll), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.statusBars()
                    | WindowInsetsCompat.Type.captionBar());
            // Left and right as well: held sideways the navigation bar is down
            // one side of the screen, and padding only the top and bottom slid
            // the content under it.
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        checkPermissions();
    }

    private void checkPermissions() {
        // using scoped storage, write external storage permission is not needed

        // camera is not used, sms permission not obtainable with google play install
        //disableToggleIfGranted(Manifest.permission.CAMERA, toggleCamera, toggleCameraWrap);
        //disableToggleIfGranted(Manifest.permission.RECEIVE_SMS, toggleSms, toggleSmsWrap);
        toggleCameraWrap.setVisibility(View.GONE);
        toggleSmsWrap.setVisibility(View.GONE);

        disableToggleIfGranted(Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE, toggleNotificationListener, toggleNotificationListenerWrap);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            disableToggleIfGranted(Manifest.permission.POST_NOTIFICATIONS, toggleNotification, toggleNotificationWrap);
        }
        else {
            toggleNotificationWrap.setVisibility(View.GONE);
        }
    }

    private void disableToggleIfGranted(String permission, CompoundButton toggleButton, ViewGroup wrapLayout) {
        if (isGranted(permission)) {
            toggleButton.setChecked(true);
            toggleButton.setEnabled(false);
            wrapLayout.setBackgroundResource(0);
        } else if (permission.equals(requestedPermission)) {
            wrapLayout.setBackgroundResource(R.drawable.highlight_border);
        }
    }

    @Click(R.id.toggleCamera)
    public void onGrantCamera() {
        requestPermission(Manifest.permission.CAMERA, toggleCamera);
    }

    @Click(R.id.toggleSms)
    public void onGrantSms() {
        requestPermission(Manifest.permission.RECEIVE_SMS, toggleSms);
    }

    @Click(R.id.toggleNotification)
    public void onGrantNotification() {
        requestPermission(Manifest.permission.POST_NOTIFICATIONS, toggleNotification);
    }

    @Click(R.id.toggleNotificationListener)
    public void onGrantNotificationListener() {
        requestPermission(Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE, toggleNotificationListener);
    }

    private void requestPermission(String permission, CompoundButton toggleButton) {
        toggleButton.setChecked(false);
        if (permission.equals(Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE)) {
            startActivityForResult(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"), 0);
            return;
        }
        // Android asks once, and after a refusal it will not ask again however
        // often the app requests it: the call returns and nothing happens, so
        // the switch looks broken. When that is the state, this opens the page
        // in the phone's own settings where the answer can still be changed.
        if (wasAskedBefore(permission)
                && !ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
            Toast.makeText(this, R.string.permission_ask_in_settings, Toast.LENGTH_LONG).show();
            openTheSettingsPage(permission);
            return;
        }
        rememberAsked(permission);
        ActivityCompat.requestPermissions(this, new String[]{permission}, 0);
    }

    /** The phone's own page for this permission, or failing that the app's page. */
    private void openTheSettingsPage(String permission) {
        try {
            if (Manifest.permission.POST_NOTIFICATIONS.equals(permission)) {
                Intent i = new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                i.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName());
                startActivity(i);
                return;
            }
        } catch (Exception ignored) {
            // Some phones do not have that page; the one below they all have.
        }
        try {
            Intent i = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            i.setData(android.net.Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception ignored) {
        }
    }

    /**
     * Whether the phone has already put this question once.
     * <p>
     * Kept here because Android cannot be asked: before the first request and
     * after a final refusal it gives the same answer, and the two need telling
     * apart.
     */
    private boolean wasAskedBefore(String permission) {
        return androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean("asked_" + permission, false);
    }

    public static void rememberAsked(android.content.Context context, String permission) {
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
                .edit().putBoolean("asked_" + permission, true).apply();
    }

    private void rememberAsked(String permission) {
        rememberAsked(this, permission);
    }

    private boolean isGranted(String permission) {
        if (permission.equals(Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE)) {
            return NotificationManagerCompat.getEnabledListenerPackages(this).contains(getPackageName());
        }

        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        checkPermissions();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        checkPermissions();
    }
}