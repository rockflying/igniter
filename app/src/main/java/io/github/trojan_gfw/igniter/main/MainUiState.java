package io.github.trojan_gfw.igniter.main;

import io.github.trojan_gfw.igniter.ProxyService;
import io.github.trojan_gfw.igniter.TrojanConfig;

/**
 * UI state data class for MainActivity.
 * Contains all the state information needed to render the main UI.
 */
public class MainUiState {
    public static final long INVALID_PORT = -1L;

    private final @ProxyService.ProxyState int proxyState;
    private final long proxyPort;
    private final boolean inputEnabled;
    private final TrojanConfig config;

    public MainUiState(@ProxyService.ProxyState int proxyState, long proxyPort, TrojanConfig config) {
        this.proxyState = proxyState;
        this.proxyPort = proxyPort;
        this.inputEnabled = (proxyState == ProxyService.STATE_NONE || proxyState == ProxyService.STOPPED);
        this.config = config;
    }

    public @ProxyService.ProxyState int getProxyState() {
        return proxyState;
    }

    public long getProxyPort() {
        return proxyPort;
    }

    public boolean isInputEnabled() {
        return inputEnabled;
    }

    public TrojanConfig getConfig() {
        return config;
    }

    public boolean isProxyRunning() {
        return proxyState == ProxyService.STARTED || proxyState == ProxyService.STARTING;
    }

    public boolean isPortValid() {
        return proxyPort >= 0L && proxyPort <= 65535;
    }

    /**
     * Creates a new MainUiState with the same config but updated proxy state.
     */
    public MainUiState withProxyState(@ProxyService.ProxyState int newState, long newPort) {
        return new MainUiState(newState, newPort, config);
    }

    /**
     * Creates a new MainUiState with the same proxy state but updated config.
     */
    public MainUiState withConfig(TrojanConfig newConfig) {
        return new MainUiState(proxyState, proxyPort, newConfig);
    }

    /**
     * Creates initial state with no proxy running and default config.
     */
    public static MainUiState initial(TrojanConfig config) {
        return new MainUiState(ProxyService.STATE_NONE, INVALID_PORT, config);
    }
}
