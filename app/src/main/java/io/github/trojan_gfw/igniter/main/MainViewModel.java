package io.github.trojan_gfw.igniter.main;

import android.content.Context;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import io.github.trojan_gfw.igniter.Globals;
import io.github.trojan_gfw.igniter.LogHelper;
import io.github.trojan_gfw.igniter.ProxyService;
import io.github.trojan_gfw.igniter.TrojanConfig;
import io.github.trojan_gfw.igniter.TrojanHelper;
import io.github.trojan_gfw.igniter.TrojanURLHelper;
import io.github.trojan_gfw.igniter.TrojanURLParseResult;
import io.github.trojan_gfw.igniter.common.constants.Constants;
import io.github.trojan_gfw.igniter.common.os.Task;
import io.github.trojan_gfw.igniter.common.os.Threads;
import io.github.trojan_gfw.igniter.common.utils.PreferenceUtils;
import io.github.trojan_gfw.igniter.connection.TrojanConnection;
import io.github.trojan_gfw.igniter.proxy.aidl.ITrojanService;
import io.github.trojan_gfw.igniter.servers.data.ServerListDataManager;
import io.github.trojan_gfw.igniter.servers.data.ServerListDataSource;
import io.github.trojan_gfw.igniter.tile.ProxyHelper;

/**
 * ViewModel for MainActivity. Manages proxy state, configuration data, and service connection.
 */
public class MainViewModel extends ViewModel implements TrojanConnection.Callback {
    private static final String TAG = "MainViewModel";
    private static final String CONNECTION_TEST_URL = "https://www.google.com";

    private final MutableLiveData<MainUiState> uiState = new MutableLiveData<>();
    private final MutableLiveData<TestResult> testResult = new MutableLiveData<>();
    private final MutableLiveData<String> shareLink = new MutableLiveData<>("");
    private final MutableLiveData<SaveResult> saveResult = new MutableLiveData<>();

    private final TrojanConnection connection = new TrojanConnection(false);
    private final Object lock = new Object();
    private volatile ITrojanService trojanService;
    private ServerListDataSource serverListDataManager;

    public MainViewModel() {
        TrojanConfig config = Globals.getTrojanConfigInstance();
        uiState.setValue(MainUiState.initial(config));
    }

    /**
     * Initialize the ViewModel with context-dependent resources.
     * Should be called from Activity.onCreate().
     */
    public void init(Context context) {
        serverListDataManager = new ServerListDataManager(Globals.getTrojanConfigListPath(), false, "", 0L);
        connection.connect(context, this);

        Threads.instance().runOnWorkThread(new Task() {
            @Override
            public void onRun() {
                PreferenceUtils.putBooleanPreference(context.getContentResolver(),
                        Uri.parse(Constants.PREFERENCE_URI),
                        Constants.PREFERENCE_KEY_FIRST_START, false);
            }
        });
    }

    public LiveData<MainUiState> getUiState() {
        return uiState;
    }

    public LiveData<TestResult> getTestResult() {
        return testResult;
    }

    public LiveData<String> getShareLink() {
        return shareLink;
    }

    public LiveData<SaveResult> getSaveResult() {
        return saveResult;
    }

    public @ProxyService.ProxyState int getProxyState() {
        MainUiState state = uiState.getValue();
        return state != null ? state.getProxyState() : ProxyService.STATE_NONE;
    }

    public long getProxyPort() {
        MainUiState state = uiState.getValue();
        return state != null ? state.getProxyPort() : MainUiState.INVALID_PORT;
    }

    // Configuration update methods
    public void updateRemoteServerRemark(String remark) {
        Globals.getTrojanConfigInstance().setRemoteServerRemark(remark);
        updateShareLink();
    }

    public void updateRemoteAddr(String addr) {
        Globals.getTrojanConfigInstance().setRemoteAddr(addr);
        updateShareLink();
    }

    public void updateSNI(String sni) {
        Globals.getTrojanConfigInstance().setSNI(sni);
        updateShareLink();
    }

    public void updateRemotePort(int port) {
        Globals.getTrojanConfigInstance().setRemotePort(port);
        updateShareLink();
    }

    public void updatePassword(String password) {
        Globals.getTrojanConfigInstance().setPassword(password);
        updateShareLink();
    }

    public void updateEnableIpv6(boolean enable) {
        Globals.getTrojanConfigInstance().setEnableIpv6(enable);
    }

    public void updateVerifyCert(boolean verify) {
        Globals.getTrojanConfigInstance().setVerifyCert(verify);
    }

    private void updateShareLink() {
        String url = TrojanURLHelper.GenerateTrojanURL(Globals.getTrojanConfigInstance());
        if (url != null) {
            shareLink.setValue(url);
        }
    }

    /**
     * Apply a new configuration from external source (e.g., server list selection).
     */
    @MainThread
    public void applyConfig(TrojanConfig config) {
        TrojanConfig ins = Globals.getTrojanConfigInstance();
        ins.setRemoteServerRemark(config.getRemoteServerRemark());
        ins.setRemoteAddr(config.getRemoteAddr());
        ins.setSNI(config.getSNI());
        ins.setRemotePort(config.getRemotePort());
        ins.setPassword(config.getPassword());
        ins.setEnableIpv6(config.getEnableIpv6());
        ins.setVerifyCert(config.getVerifyCert());

        TrojanHelper.WriteTrojanConfig(ins, Globals.getTrojanConfigPath());

        MainUiState currentState = uiState.getValue();
        if (currentState != null) {
            uiState.setValue(currentState.withConfig(ins));
        }

        shareLink.setValue(TrojanURLHelper.GenerateTrojanURL(config));
    }

    /**
     * Parse and apply a Trojan URL.
     */
    public boolean applyTrojanUrl(String url) {
        TrojanURLParseResult parseResult = TrojanURLHelper.ParseTrojanURL(url);
        if (parseResult != null) {
            TrojanConfig newConfig = TrojanURLHelper.CombineTrojanURLParseResultToTrojanConfig(
                    parseResult, Globals.getTrojanConfigInstance());
            Globals.setTrojanConfigInstance(newConfig);

            MainUiState currentState = uiState.getValue();
            if (currentState != null) {
                uiState.postValue(currentState.withConfig(newConfig));
            }
            return true;
        }
        return false;
    }

    /**
     * Start or stop the proxy service based on current state.
     */
    public void toggleProxy(Context context) {
        if (!Globals.getTrojanConfigInstance().isValidRunningConfig()) {
            return;
        }

        int currentProxyState = getProxyState();
        if (currentProxyState == ProxyService.STATE_NONE || currentProxyState == ProxyService.STOPPED) {
            startProxy(context);
        } else if (currentProxyState == ProxyService.STARTED) {
            stopProxy(context);
        }
    }

    /**
     * Check if a VPN prepare intent is needed before starting proxy.
     */
    public boolean needsVpnPrepare() {
        return Globals.getTrojanConfigInstance().isValidRunningConfig();
    }

    /**
     * Start the proxy service.
     */
    public void startProxy(Context context) {
        TrojanHelper.WriteTrojanConfig(Globals.getTrojanConfigInstance(), Globals.getTrojanConfigPath());
        TrojanHelper.ShowConfig(Globals.getTrojanConfigPath());
        ProxyHelper.startProxyService(context);
    }

    /**
     * Stop the proxy service.
     */
    public void stopProxy(Context context) {
        ProxyHelper.stopProxyService(context);
    }

    /**
     * Save the current configuration to the server list.
     */
    public void saveConfig() {
        if (!Globals.getTrojanConfigInstance().isValidRunningConfig()) {
            saveResult.setValue(new SaveResult(false));
            return;
        }

        Threads.instance().runOnWorkThread(new Task() {
            @Override
            public void onRun() {
                TrojanConfig config = Globals.getTrojanConfigInstance();
                TrojanHelper.WriteTrojanConfig(config, Globals.getTrojanConfigPath());
                serverListDataManager.saveServerConfig(config);
                saveResult.postValue(new SaveResult(true));
            }
        });
    }

    /**
     * Test the proxy connection.
     */
    public void testConnection() {
        ITrojanService service;
        synchronized (lock) {
            service = trojanService;
        }
        if (service == null) {
            testResult.setValue(new TestResult(CONNECTION_TEST_URL, false, 0L, "Service not available"));
        } else {
            try {
                service.testConnection(CONNECTION_TEST_URL);
            } catch (RemoteException e) {
                testResult.setValue(new TestResult(CONNECTION_TEST_URL, false, 0L, "Service error"));
                Log.i(TAG, "testConnection: ", e);
            }
        }
    }

    /**
     * Show develop info in Logcat.
     */
    public void showDevelopInfoInLogcat() {
        LogHelper.showDevelopInfoInLogcat();
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

    /**
     * Get proxy connection info for server list activity.
     */
    public ProxyConnectionInfo getProxyConnectionInfo() {
        ITrojanService service;
        synchronized (lock) {
            service = trojanService;
        }
        if (service != null) {
            try {
                boolean proxyOn = service.getState() == ProxyService.STARTED;
                String proxyHost = service.getProxyHost();
                long proxyPort = service.getProxyPort();
                return new ProxyConnectionInfo(proxyOn, proxyHost, proxyPort);
            } catch (RemoteException e) {
                Log.i(TAG, "getProxyConnectionInfo: ", e);
            }
        }
        return new ProxyConnectionInfo(false, null, 0L);
    }

    // TrojanConnection.Callback implementation
    @Override
    public void onServiceConnected(ITrojanService service) {
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
                    MainUiState currentState = uiState.getValue();
                    if (currentState != null) {
                        long newPort = (state == ProxyService.STARTED || state == ProxyService.STARTING)
                                ? port : MainUiState.INVALID_PORT;
                        uiState.postValue(currentState.withProxyState(state, newPort));
                    }
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
        MainUiState currentState = uiState.getValue();
        if (currentState != null) {
            uiState.postValue(currentState.withProxyState(currentState.getProxyState(), MainUiState.INVALID_PORT));
        }
    }

    @Override
    public void onStateChanged(int state, String msg) {
        LogHelper.i(TAG, "onStateChanged# state: " + state + " msg: " + msg);
        long port = MainUiState.INVALID_PORT;
        try {
            org.json.JSONObject msgJson = new org.json.JSONObject(msg);
            port = msgJson.optLong(ProxyService.STATE_MSG_KEY_PORT, MainUiState.INVALID_PORT);
        } catch (org.json.JSONException e) {
            Log.i(TAG, "onStateChanged: ", e);
        }

        MainUiState currentState = uiState.getValue();
        if (currentState != null) {
            uiState.postValue(currentState.withProxyState(state, port));
        }
    }

    @Override
    public void onTestResult(String testUrl, boolean connected, long delay, @NonNull String error) {
        testResult.postValue(new TestResult(testUrl, connected, delay, error));
    }

    @Override
    public void onBinderDied() {
        LogHelper.i(TAG, "onBinderDied");
    }

    public void disconnect(Context context) {
        connection.disconnect(context);
    }

    public void reconnect(Context context) {
        connection.disconnect(context);
        connection.connect(context, this);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        synchronized (lock) {
            trojanService = null;
        }
    }

    /**
     * Result class for connection tests.
     */
    public static class TestResult {
        public final String testUrl;
        public final boolean connected;
        public final long delay;
        public final String error;

        public TestResult(String testUrl, boolean connected, long delay, String error) {
            this.testUrl = testUrl;
            this.connected = connected;
            this.delay = delay;
            this.error = error;
        }
    }

    /**
     * Result class for save operations.
     */
    public static class SaveResult {
        public final boolean success;

        public SaveResult(boolean success) {
            this.success = success;
        }
    }

    /**
     * Proxy connection info for server list activity.
     */
    public static class ProxyConnectionInfo {
        public final boolean proxyOn;
        public final String proxyHost;
        public final long proxyPort;

        public ProxyConnectionInfo(boolean proxyOn, String proxyHost, long proxyPort) {
            this.proxyOn = proxyOn;
            this.proxyHost = proxyHost;
            this.proxyPort = proxyPort;
        }
    }
}
