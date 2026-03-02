package io.github.trojan_gfw.igniter;


import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.net.VpnService;
import android.os.Bundle;
import android.os.RemoteException;
import android.text.InputType;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import io.github.trojan_gfw.igniter.common.constants.Constants;
import io.github.trojan_gfw.igniter.common.os.Task;
import io.github.trojan_gfw.igniter.common.os.Threads;
import io.github.trojan_gfw.igniter.common.utils.AnimationUtils;
import io.github.trojan_gfw.igniter.common.utils.DisplayUtils;
import io.github.trojan_gfw.igniter.common.utils.PreferenceUtils;
import io.github.trojan_gfw.igniter.common.utils.SnackbarUtils;
import io.github.trojan_gfw.igniter.connection.TrojanConnection;
import io.github.trojan_gfw.igniter.databinding.ActivityMainBinding;
import io.github.trojan_gfw.igniter.exempt.activity.ExemptAppActivity;
import io.github.trojan_gfw.igniter.proxy.aidl.ITrojanService;
import io.github.trojan_gfw.igniter.servers.activity.ServerListActivity;
import io.github.trojan_gfw.igniter.servers.data.ServerListDataManager;
import io.github.trojan_gfw.igniter.servers.data.ServerListDataSource;
import io.github.trojan_gfw.igniter.settings.activity.SettingsActivity;
import io.github.trojan_gfw.igniter.tile.ProxyHelper;


public class MainActivity extends AppCompatActivity implements TrojanConnection.Callback {
    private static final String TAG = "MainActivity";
    private static final long INVALID_PORT = -1L;
    private static final String CONNECTION_TEST_URL = "https://www.google.com";

    private String shareLink;
    private ActivityMainBinding binding;
    private @ProxyService.ProxyState int proxyState = ProxyService.STATE_NONE;
    private long currentProxyPort;
    private final TrojanConnection connection = new TrojanConnection(false);
    private final Object lock = new Object();
    private volatile ITrojanService trojanService;
    private ServerListDataSource serverListDataManager;
    private AlertDialog linkDialog;
    private EditText trojanURLText;
    private ActivityResultLauncher<Intent> goToServerListActivityResultLauncher;
    private ActivityResultLauncher<Intent> exemptAppSettingsActivityResultLauncher;
    private ActivityResultLauncher<Intent> startProxyActivityResultLauncher;

    private final TextViewListener remoteServerRemarkTextListener = new TextViewListener() {
        @Override
        protected void onTextChanged(String before, String old, String aNew, String after) {
            startUpdates();
            if (binding.remoteServerRemarkText.hasFocus()) {
                TrojanConfig ins = Globals.getTrojanConfigInstance();
                ins.setRemoteServerRemark(binding.remoteServerRemarkText.getText().toString());
            }
            endUpdates();
        }
    };

    private final TextViewListener remoteAddrTextListener = new TextViewListener() {
        @Override
        protected void onTextChanged(String before, String old, String aNew, String after) {
            startUpdates();
            if (binding.remoteAddrText.hasFocus()) {
                TrojanConfig ins = Globals.getTrojanConfigInstance();
                String remoteAddrRawStr = binding.remoteAddrText.getText().toString();
                ins.setRemoteAddr(remoteAddrRawStr.trim());
            }
            endUpdates();
        }
    };

    private final TextViewListener remoteServerSNITextListener = new TextViewListener() {
        @Override
        protected void onTextChanged(String before, String old, String aNew, String after) {
            startUpdates();
            if (binding.remoteServerSNIText.hasFocus()) {
                TrojanConfig ins = Globals.getTrojanConfigInstance();
                String remoteServerSNIRawStr = binding.remoteServerSNIText.getText().toString();
                ins.setSNI(remoteServerSNIRawStr.trim());
            }
            endUpdates();
        }
    };

    private final TextViewListener remotePortTextListener = new TextViewListener() {
        @Override
        protected void onTextChanged(String before, String old, String aNew, String after) {
            startUpdates();
            if (binding.remotePortText.hasFocus()) {
                TrojanConfig ins = Globals.getTrojanConfigInstance();
                String portStr = binding.remotePortText.getText().toString();
                try {
                    int port = Integer.parseInt(portStr);
                    ins.setRemotePort(port);
                } catch (NumberFormatException e) {
                    Log.i(TAG, "onTextChanged: ", e);
                }
            }
            endUpdates();
        }
    };

    private final TextViewListener passwordTextListener = new TextViewListener() {
        @Override
        protected void onTextChanged(String before, String old, String aNew, String after) {
            startUpdates();
            if (binding.passwordText.hasFocus()) {
                TrojanConfig ins = Globals.getTrojanConfigInstance();
                ins.setPassword(binding.passwordText.getText().toString());
            }
            endUpdates();
        }
    };

    private void copyRawResourceToDir(int resId, String destPathName, boolean override) {
        File file = new File(destPathName);
        if (override || !file.exists()) {
            try {
                try (InputStream is = getResources().openRawResource(resId);
                     FileOutputStream fos = new FileOutputStream(file)) {
                    byte[] buf = new byte[1024];
                    int len;
                    while ((len = is.read(buf)) > 0) {
                        fos.write(buf, 0, len);
                    }
                }
            } catch (Exception e) {
                LogHelper.e(TAG, "Failed to copy raw resource to: " + destPathName, e);
            }
        }
    }

    private void updateViews(int state) {
        proxyState = state;
        boolean inputEnabled;
        switch (state) {
            case ProxyService.STARTING: {
                inputEnabled = false;
                binding.startStopButton.setText(R.string.button_service__starting);
                binding.startStopButton.setEnabled(false);
                break;
            }
            case ProxyService.STARTED: {
                inputEnabled = false;
                binding.startStopButton.setText(R.string.button_service__stop);
                binding.startStopButton.setEnabled(true);
                break;
            }
            case ProxyService.STOPPING: {
                inputEnabled = false;
                binding.startStopButton.setText(R.string.button_service__stopping);
                binding.startStopButton.setEnabled(false);
                break;
            }
            default: {
                inputEnabled = true;
                binding.startStopButton.setText(R.string.button_service__start);
                binding.startStopButton.setEnabled(true);
                break;
            }
        }
        binding.remoteServerRemarkText.setEnabled(inputEnabled);
        binding.remoteAddrText.setEnabled(inputEnabled);
        binding.remoteServerSNIText.setEnabled(inputEnabled);
        binding.remotePortText.setEnabled(inputEnabled);
        binding.ipv6Switch.setEnabled(inputEnabled);
        binding.passwordText.setEnabled(inputEnabled);
        binding.verifySwitch.setEnabled(inputEnabled);
        binding.clashSwitch.setEnabled(inputEnabled);
        binding.allowLanSwitch.setEnabled(inputEnabled);
    }

    private void applyConfigInstance(TrojanConfig config) {
        TrojanConfig ins = Globals.getTrojanConfigInstance();
        if (config != null) {
            String remoteServerRemark = config.getRemoteServerRemark();
            String remoteAddress = config.getRemoteAddr();
            String remoteServerSNI = config.getSNI();
            int remotePort = config.getRemotePort();
            String password = config.getPassword();
            boolean verifyCert = config.getVerifyCert();
            boolean enableIpv6 = config.getEnableIpv6();

            ins.setRemoteServerRemark(remoteServerRemark);
            ins.setSNI(remoteServerSNI);
            ins.setRemoteAddr(remoteAddress);
            ins.setRemotePort(remotePort);
            ins.setPassword(password);
            ins.setVerifyCert(verifyCert);
            ins.setEnableIpv6(enableIpv6);

            binding.remoteServerRemarkText.setText(remoteServerRemark);
            binding.remoteServerSNIText.setText(remoteServerSNI);
            binding.passwordText.setText(password);
            binding.remotePortText.setText(String.valueOf(remotePort));
            binding.remoteAddrText.setText(remoteAddress);
            binding.remoteAddrText.setSelection(binding.remoteAddrText.length());
            binding.verifySwitch.setChecked(verifyCert);
            binding.ipv6Switch.setChecked(enableIpv6);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final int screenWidth = DisplayUtils.getScreenWidth();
        if (screenWidth >= 1080) {
            setContentView(R.layout.activity_main);
        } else {
            setContentView(R.layout.activity_main_720);
        }
        // Bind views after setContentView
        binding = ActivityMainBinding.bind(findViewById(R.id.rootLayout));
        setSupportActionBar(binding.toolbar);

        // 沉浸式状态栏：状态栏透明，Toolbar 延伸到状态栏下方
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        androidx.core.view.WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(false);

        // 给 Toolbar 添加状态栏高度的顶部内边距
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar, (v, insets) -> {
            androidx.core.graphics.Insets statusBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars());
            v.setPadding(v.getPaddingLeft(), statusBars.top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });

        goToServerListActivityResultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        Intent data = result.getData();
                        if (result.getResultCode() == RESULT_OK && data != null) {
                            shareLink = "";
                            final TrojanConfig selectedConfig = getParcelableExtraCompat(data, ServerListActivity.KEY_TROJAN_CONFIG, TrojanConfig.class);
                            if (selectedConfig != null) {
                                LogHelper.e("gotoServer: ", selectedConfig.toString());

                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        TrojanConfig ins = Globals.getTrojanConfigInstance();
                                        ins.setRemoteServerRemark(selectedConfig.getRemoteServerRemark());
                                        ins.setRemoteAddr(selectedConfig.getRemoteAddr());
                                        ins.setSNI(selectedConfig.getSNI());
                                        ins.setRemotePort(selectedConfig.getRemotePort());
                                        ins.setPassword(selectedConfig.getPassword());
                                        ins.setEnableIpv6(selectedConfig.getEnableIpv6());
                                        ins.setVerifyCert(selectedConfig.getVerifyCert());
                                        TrojanHelper.WriteTrojanConfig(Globals.getTrojanConfigInstance(), Globals.getTrojanConfigPath());
                                        applyConfigInstance(ins);
                                    }
                                });
                                shareLink = TrojanURLHelper.GenerateTrojanURL(selectedConfig);
                            }
                        }
                    }
                });

        exemptAppSettingsActivityResultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result.getResultCode() == RESULT_OK) {
                            if (ProxyService.STARTED == proxyState) {
                                SnackbarUtils.showTextLong(binding.rootScrollView, R.string.main_restart_proxy_service_tip);
                            }
                        }
                    }
                });

        startProxyActivityResultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result.getResultCode() == RESULT_OK)
                            ProxyHelper.startProxyService(getApplicationContext());
                    }
                });

        copyRawResourceToDir(R.raw.cacert, Globals.getCaCertPath(), true);
        copyRawResourceToDir(R.raw.country, Globals.getCountryMmdbPath(), true);
        copyRawResourceToDir(R.raw.clash_config, Globals.getClashConfigPath(), false);

        binding.remoteServerRemarkText.addTextChangedListener(remoteServerRemarkTextListener);
        binding.remoteAddrText.addTextChangedListener(remoteAddrTextListener);
        binding.remoteServerSNIText.addTextChangedListener(remoteServerSNITextListener);
        binding.remotePortText.addTextChangedListener(remotePortTextListener);

        binding.passwordText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    binding.passwordText.setInputType(EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_PASSWORD);
                } else {
                    binding.passwordText.setInputType(EditorInfo.TYPE_CLASS_TEXT);
                    binding.passwordText.setSelection(binding.passwordText.getText().length());
                }
            }
        });

        boolean enableClash = PreferenceUtils.getBooleanPreference(getContentResolver(),
                Uri.parse(Constants.PREFERENCE_URI), Constants.PREFERENCE_KEY_ENABLE_CLASH, true);
        binding.clashSwitch.setChecked(enableClash);
        binding.clashSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                PreferenceUtils.putBooleanPreference(getContentResolver(),
                        Uri.parse(Constants.PREFERENCE_URI), Constants.PREFERENCE_KEY_ENABLE_CLASH,
                        isChecked);
            }
        });

        boolean allowLan = PreferenceUtils.getBooleanPreference(getContentResolver(),
                Uri.parse(Constants.PREFERENCE_URI), Constants.PREFERENCE_KEY_ALLOW_LAN, false);
        binding.allowLanSwitch.setChecked(allowLan);
        binding.allowLanSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                PreferenceUtils.putBooleanPreference(getContentResolver(),
                        Uri.parse(Constants.PREFERENCE_URI), Constants.PREFERENCE_KEY_ALLOW_LAN,
                        isChecked);
            }
        });

        binding.passwordText.addTextChangedListener(passwordTextListener);

        binding.ipv6Switch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                TrojanConfig ins = Globals.getTrojanConfigInstance();
                ins.setEnableIpv6(isChecked);
            }
        });

        binding.verifySwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                TrojanConfig ins = Globals.getTrojanConfigInstance();
                ins.setVerifyCert(isChecked);
            }
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Trojan URL");

        trojanURLText = new EditText(this);

        trojanURLText.setInputType(InputType.TYPE_CLASS_TEXT);
        trojanURLText.setSingleLine(false);
        trojanURLText.setSelectAllOnFocus(true);

        FrameLayout container = new FrameLayout(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = getResources().getDimensionPixelSize(R.dimen.dialog_margin);
        params.rightMargin = getResources().getDimensionPixelSize(R.dimen.dialog_margin);
        trojanURLText.setLayoutParams(params);
        container.addView(trojanURLText);
        builder.setView(container);

        builder.setPositiveButton(R.string.common_update, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                TrojanURLParseResult parseResult = TrojanURLHelper.ParseTrojanURL(trojanURLText.getText().toString());
                if (parseResult != null) {
                    Globals.setTrojanConfigInstance(TrojanURLHelper.CombineTrojanURLParseResultToTrojanConfig(parseResult, Globals.getTrojanConfigInstance()));
                    applyConfigInstance(Globals.getTrojanConfigInstance());
                }
                dialog.cancel();
            }
        });
        builder.setNegativeButton(R.string.common_cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        linkDialog = builder.create();

        TextViewListener trojanConfigChangedTextViewListener = new TextViewListener() {
            @Override
            protected void onTextChanged(String before, String old, String aNew, String after) {
                startUpdates();
                String str = TrojanURLHelper.GenerateTrojanURL(Globals.getTrojanConfigInstance());
                if (str != null) {
                    shareLink = str;
                }
                endUpdates();
            }
        };

        binding.remoteAddrText.addTextChangedListener(trojanConfigChangedTextViewListener);
        binding.remoteServerSNIText.addTextChangedListener(trojanConfigChangedTextViewListener);
        binding.remotePortText.addTextChangedListener(trojanConfigChangedTextViewListener);
        binding.passwordText.addTextChangedListener(trojanConfigChangedTextViewListener);

        binding.startStopButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!Globals.getTrojanConfigInstance().isValidRunningConfig()) {
                    Toast.makeText(MainActivity.this,
                            R.string.invalid_configuration,
                            Toast.LENGTH_LONG).show();
                    return;
                }
                if (proxyState == ProxyService.STATE_NONE || proxyState == ProxyService.STOPPED) {
                    TrojanHelper.WriteTrojanConfig(
                            Globals.getTrojanConfigInstance(),
                            Globals.getTrojanConfigPath()
                    );
                    TrojanHelper.ShowConfig(Globals.getTrojanConfigPath());
                    Intent i = VpnService.prepare(getApplicationContext());
                    if (i != null) {
                        startProxyActivityResultLauncher.launch(i);
                    } else {
                        ProxyHelper.startProxyService(getApplicationContext());
                    }
                } else if (proxyState == ProxyService.STARTED) {
                    ProxyHelper.stopProxyService(getApplicationContext());
                }
            }
        });
        serverListDataManager = new ServerListDataManager(Globals.getTrojanConfigListPath(), false, "", 0L);
        connection.connect(this, this);
        Threads.instance().runOnWorkThread(new Task() {
            @Override
            public void onRun() {
                PreferenceUtils.putBooleanPreference(getContentResolver(),
                        Uri.parse(Constants.PREFERENCE_URI),
                        Constants.PREFERENCE_KEY_FIRST_START, false);
            }
        });
        GestureDetector gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                swayTheHorse();
                return true;
            }
        });
        binding.imageView.setOnTouchListener((v, event) -> gestureDetector.onTouchEvent(event));
        binding.copyPortBtn.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            String portStr = String.valueOf(currentProxyPort);
            ClipData data = ClipData.newPlainText("port", portStr);
            cm.setPrimaryClip(data);
            SnackbarUtils.showTextShort(binding.rootScrollView,
                    getString(R.string.main_proxy_port_copied_to_clipboard, portStr));
        });
    }

    private void swayTheHorse() {
        binding.imageView.clearAnimation();
        AnimationUtils.sway(binding.imageView, 60f, 500L, 4f);
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkTrojanURLFromClipboard();
    }

    private void checkTrojanURLFromClipboard() {
        Threads.instance().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final ClipboardManager clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (!clipboardManager.hasPrimaryClip()) {
                    return;
                }
                ClipData clipData = clipboardManager.getPrimaryClip();
                if (clipData == null || clipData.getItemCount() == 0) {
                    return;
                }
                final CharSequence clipboardText = clipData.getItemAt(0).coerceToText(MainActivity.this);
                TrojanURLParseResult parseResult = TrojanURLHelper.ParseTrojanURL(clipboardText.toString());
                if (parseResult == null) {
                    return;
                }

                if (clipboardManager.hasPrimaryClip()) {
                    clipboardManager.setPrimaryClip(ClipData.newPlainText("", ""));
                }
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(R.string.clipboard_import_tip)
                        .setPositiveButton(R.string.common_confirm, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                TrojanConfig newConfig = TrojanURLHelper.CombineTrojanURLParseResultToTrojanConfig(parseResult, Globals.getTrojanConfigInstance());
                                Globals.setTrojanConfigInstance(newConfig);
                                applyConfigInstance(newConfig);
                            }
                        })
                        .setNegativeButton(R.string.common_cancel, null)
                        .create()
                        .show();
            }
        });
    }

    @UiThread
    private void updatePortInfo(long port) {
        currentProxyPort = port;
        if (port >= 0L && port <= 65535) {
            binding.copyPortBtn.setText(getString(R.string.notification_listen_port, String.valueOf(port)));
            binding.copyPortBtn.setVisibility(View.VISIBLE);
        } else {
            binding.copyPortBtn.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void onServiceConnected(final ITrojanService service) {
        LogHelper.i(TAG, "onServiceConnected");
        synchronized (lock) {
            trojanService = service;
        }
        Threads.instance().runOnWorkThread(new Task() {
            @Override
            public void onRun() {
                try {
                    final int state = service.getState();
                    final long port = service.getProxyPort();
                    runOnUiThread(() -> {
                        updateViews(state);
                        if (ProxyService.STARTED == state || ProxyService.STARTING == state) {
                            updatePortInfo(port);
                        } else {
                            updatePortInfo(INVALID_PORT);
                        }
                    });
                } catch (RemoteException e) {
                    Log.e(TAG, "onServiceConnected: ", e);
                }
            }
        });
    }

    @Override
    public void onServiceDisconnected() {
        LogHelper.i(TAG, "onServiceDisconnected");
        synchronized (lock) {
            trojanService = null;
        }
        runOnUiThread(() -> updatePortInfo(INVALID_PORT));
    }

    @Override
    public void onStateChanged(int state, String msg) {
        LogHelper.i(TAG, "onStateChanged# state: " + state + " msg: " + msg);
        updateViews(state);
        try {
            JSONObject msgJson = new JSONObject(msg);
            long port = msgJson.optLong(ProxyService.STATE_MSG_KEY_PORT, INVALID_PORT);
            updatePortInfo(port);
        } catch (JSONException e) {
            Log.i(TAG, "onStateChanged: ", e);
        }
    }

    @Override
    public void onTestResult(final String testUrl, final boolean connected, final long delay, @NonNull final String error) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                showTestConnectionResult(testUrl, connected, delay, error);
            }
        });
    }

    private void showTestConnectionResult(String testUrl, boolean connected, long delay, @NonNull String error) {
        if (connected) {
            Toast.makeText(getApplicationContext(), getString(R.string.connected_to__in__ms,
                    testUrl, String.valueOf(delay)), Toast.LENGTH_LONG).show();
        } else {
            LogHelper.e(TAG, "TestError: " + error);
            Toast.makeText(getApplicationContext(),
                    getString(R.string.failed_to_connect_to__,
                            testUrl, error),
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onBinderDied() {
        LogHelper.i(TAG, "onBinderDied");
        connection.disconnect(this);
        connection.connect(this, this);
    }

    private void testConnection() {
        ITrojanService service;
        synchronized (lock) {
            service = trojanService;
        }
        if (service == null) {
            showTestConnectionResult(CONNECTION_TEST_URL, false, 0L, getString(R.string.trojan_service_not_available));
        } else {
            try {
                service.testConnection(CONNECTION_TEST_URL);
            } catch (RemoteException e) {
                showTestConnectionResult(CONNECTION_TEST_URL, false, 0L, getString(R.string.trojan_service_error));
                Log.i(TAG, "testConnection: ", e);
            }
        }
    }

    private void showDevelopInfoInLogcat() {
        ITrojanService service;
        synchronized (lock) {
            service = trojanService;
        }
        if (service != null) {
            try {
                service.showDevelopInfoInLogcat();
            } catch (RemoteException e) {
                Log.i(TAG, "showDevelopInfoInLogcat: ", e);
            }
        }
    }

    private void clearEditTextFocus() {
        binding.remoteServerRemarkText.clearFocus();
        binding.remoteAddrText.clearFocus();
        binding.remoteServerSNIText.clearFocus();
        binding.remotePortText.clearFocus();
        binding.passwordText.clearFocus();
    }

    private void showSaveConfigResult(final boolean success) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(),
                        success ? R.string.main_save_success : R.string.main_save_failed,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_test_connection) {
            testConnection();
            return true;
        } else if (itemId == R.id.action_show_develop_info_logcat) {
            LogHelper.showDevelopInfoInLogcat();
            showDevelopInfoInLogcat();
            return true;
        } else if (itemId == R.id.action_save_profile) {
            if (!Globals.getTrojanConfigInstance().isValidRunningConfig()) {
                Toast.makeText(MainActivity.this, R.string.invalid_configuration, Toast.LENGTH_SHORT).show();
                return true;
            }
            Threads.instance().runOnWorkThread(new Task() {
                @Override
                public void onRun() {
                    TrojanConfig config = Globals.getTrojanConfigInstance();
                    TrojanHelper.WriteTrojanConfig(config, Globals.getTrojanConfigPath());
                    serverListDataManager.saveServerConfig(config);
                    showSaveConfigResult(true);
                }
            });
            return true;
        } else if (itemId == R.id.action_view_server_list) {
            gotoServerList();
            return true;
        } else if (itemId == R.id.action_about) {
            clearEditTextFocus();
            startActivity(AboutActivity.create(MainActivity.this));
            return true;
        } else if (itemId == R.id.action_share_link) {
            trojanURLText.setText(shareLink);
            linkDialog.show();
            trojanURLText.selectAll();
            return true;
        } else if (itemId == R.id.action_exempt_app) {
            exemptAppSettingsActivityResultLauncher.launch(ExemptAppActivity.create(this));
            return true;
        } else if (itemId == R.id.action_settings) {
            startActivity(SettingsActivity.create(this));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void gotoServerList() {
        clearEditTextFocus();

        boolean proxyOn = false;
        String proxyHost = null;
        long proxyPort = 0L;
        ITrojanService service;
        synchronized (lock) {
            service = trojanService;
        }
        if (service != null) {
            try {
                proxyOn = service.getState() == ProxyService.STARTED;
                proxyHost = service.getProxyHost();
                proxyPort = service.getProxyPort();
            } catch (RemoteException e) {
                Log.i(TAG, "gotoServerList: ", e);
            }
        }

        goToServerListActivityResultLauncher.launch(ServerListActivity.create(MainActivity.this,
                proxyOn, proxyHost, proxyPort));
    }

    @SuppressWarnings("deprecation")
    private static <T extends android.os.Parcelable> T getParcelableExtraCompat(Intent intent, String name, Class<T> clazz) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            return intent.getParcelableExtra(name, clazz);
        } else {
            return intent.getParcelableExtra(name);
        }
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        TrojanConfig cachedConfig = TrojanHelper.readTrojanConfig(Globals.getTrojanConfigPath());
        if (cachedConfig != null) {
            applyConfigInstance(cachedConfig);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        connection.disconnect(this);
        binding = null;
    }
}
