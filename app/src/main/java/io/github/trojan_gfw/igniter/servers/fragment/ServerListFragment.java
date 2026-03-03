package io.github.trojan_gfw.igniter.servers.fragment;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.MenuHost;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.github.trojan_gfw.igniter.Globals;
import io.github.trojan_gfw.igniter.R;
import io.github.trojan_gfw.igniter.TrojanConfig;
import io.github.trojan_gfw.igniter.common.app.BaseFragment;
import io.github.trojan_gfw.igniter.common.dialog.LoadingDialog;
import io.github.trojan_gfw.igniter.common.utils.SnackbarUtils;
import io.github.trojan_gfw.igniter.databinding.FragmentServerListBinding;
import io.github.trojan_gfw.igniter.qrcode.ScanQRCodeActivity;
import io.github.trojan_gfw.igniter.servers.ItemVerticalMoveCallback;
import io.github.trojan_gfw.igniter.servers.SubscribeSettingDialog;
import io.github.trojan_gfw.igniter.servers.activity.ServerListActivity;
import io.github.trojan_gfw.igniter.servers.contract.ServerListContract;

import static android.app.Activity.RESULT_OK;

public class ServerListFragment extends BaseFragment implements ServerListContract.View {
    public static final String TAG = "ServerListFragment";
    public static final String KEY_TROJAN_CONFIG = ServerListActivity.KEY_TROJAN_CONFIG;

    private ActivityResultLauncher<String> scanQRCodeRequestPermissionStartActivityLaunch;
    private ActivityResultLauncher<String> importConfigStartActivityLaunch;
    private ActivityResultLauncher<Intent> scanQRCodeGotResultStartActivityLaunch;

    private ServerListContract.Presenter mPresenter;
    private FragmentServerListBinding binding;
    private ItemTouchHelper mItemTouchHelper;
    private ServerListAdapter mServerListAdapter;
    private Dialog mImportConfigDialog;
    private Dialog mLoadingDialog;
    private boolean mBatchOperationMode;

    public ServerListFragment() {
        // Required empty public constructor
    }

    public static ServerListFragment newInstance() {
        return new ServerListFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        scanQRCodeRequestPermissionStartActivityLaunch = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                result -> {
                    if (result) {
                        scanQRCodeGotResultStartActivityLaunch.launch(ScanQRCodeActivity.create(mContext, false));
                    } else {
                        Toast.makeText(mContext.getApplicationContext(), R.string.server_list_lack_of_camera_permission, Toast.LENGTH_SHORT).show();
                    }
                });

        importConfigStartActivityLaunch = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        mPresenter.parseConfigsInFileStream(getContext(), uri);
                    }
                });

        scanQRCodeGotResultStartActivityLaunch = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        Intent data = result.getData();
                        if (data == null) return;
                        mPresenter.addServerConfig(data.getStringExtra(ScanQRCodeActivity.KEY_SCAN_CONTENT));
                    }
                });
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentServerListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews();
        initListeners();
        setupMenu();
        mPresenter.start();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void initViews() {
        FragmentActivity activity = getActivity();
        if (activity instanceof AppCompatActivity) {
            ((AppCompatActivity) activity).setSupportActionBar(binding.toolbar);
        }
        // 沉浸式状态栏：为 Toolbar 添加状态栏内边距
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar, (v, insets) -> {
            androidx.core.graphics.Insets statusBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars());
            v.setPadding(v.getPaddingLeft(), statusBars.top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });
        binding.serverListRv.setLayoutManager(new LinearLayoutManager(mContext, LinearLayoutManager.VERTICAL, false));
        binding.serverListRv.setHasFixedSize(true);
        binding.serverListRv.addItemDecoration(new DividerItemDecoration(mContext, DividerItemDecoration.VERTICAL));
        mServerListAdapter = new ServerListAdapter(getContext(), new ArrayList<>());
        mItemTouchHelper = new ItemTouchHelper(new ItemVerticalMoveCallback(mServerListAdapter));
        binding.serverListRv.setAdapter(mServerListAdapter);
    }

    private void setupMenu() {
        MenuHost menuHost = requireActivity();
        menuHost.addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
                menu.clear();
                inflater.inflate(R.menu.menu_server_list, menu);
                MenuItem qrCodeItem = menu.findItem(R.id.action_scan_qr_code).setVisible(!mBatchOperationMode);
                menu.findItem(R.id.action_import_from_file).setVisible(!mBatchOperationMode);
                menu.findItem(R.id.action_export_to_file).setVisible(!mBatchOperationMode);
                menu.findItem(R.id.action_enter_batch_mode).setVisible(!mBatchOperationMode);
                menu.findItem(R.id.action_subscribe_settings).setVisible(!mBatchOperationMode);
                menu.findItem(R.id.action_subscribe_servers).setVisible(!mBatchOperationMode);
                menu.findItem(R.id.action_exit_batch_operation).setVisible(mBatchOperationMode);
                menu.findItem(R.id.action_select_all_servers).setVisible(mBatchOperationMode);
                menu.findItem(R.id.action_deselect_all_servers).setVisible(mBatchOperationMode);
                menu.findItem(R.id.action_batch_delete_servers).setVisible(mBatchOperationMode);
                menu.findItem(R.id.action_test_all_proxy_server).setVisible(!mBatchOperationMode);
                // Tint scan QRCode icon to white.
                if (qrCodeItem.getIcon() != null) {
                    Drawable drawable = qrCodeItem.getIcon();
                    Drawable wrapper = DrawableCompat.wrap(drawable);
                    drawable.mutate();
                    DrawableCompat.setTint(wrapper, ContextCompat.getColor(mContext, android.R.color.white));
                    qrCodeItem.setIcon(drawable);
                }
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem item) {
                int itemId = item.getItemId();
                if (itemId == R.id.action_scan_qr_code) {
                    askTheWayToScanQRCode();
                    return true;
                } else if (itemId == R.id.action_import_from_file) {
                    mPresenter.displayImportFileDescription();
                    return true;
                } else if (itemId == R.id.action_export_to_file) {
                    mPresenter.exportServerListToFile();
                    return true;
                } else if (itemId == R.id.action_enter_batch_mode) {
                    mPresenter.batchOperateServerList();
                    return true;
                } else if (itemId == R.id.action_exit_batch_operation) {
                    mPresenter.saveServerList(mServerListAdapter.getData());
                    mPresenter.exitServerListBatchOperation();
                    return true;
                } else if (itemId == R.id.action_select_all_servers) {
                    mPresenter.selectAll(mServerListAdapter.getData());
                    return true;
                } else if (itemId == R.id.action_deselect_all_servers) {
                    mPresenter.deselectAll(mServerListAdapter.getData());
                    return true;
                } else if (itemId == R.id.action_batch_delete_servers) {
                    mPresenter.batchDelete();
                    return true;
                } else if (itemId == R.id.action_subscribe_settings) {
                    mPresenter.displaySubscribeSettings();
                    return true;
                } else if (itemId == R.id.action_subscribe_servers) {
                    mPresenter.updateSubscribeServers();
                    return true;
                } else if (itemId == R.id.action_test_all_proxy_server) {
                    mPresenter.pingAllProxyServer(mServerListAdapter.getData());
                    return true;
                }
                return false;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }

    private void initListeners() {
        mServerListAdapter.setOnItemClickListener(new ServerListAdapter.OnItemClickListener() {
            @Override
            public void onItemSelected(TrojanConfig config, int pos) {
                mPresenter.handleServerSelection(config);
            }

            @Override
            public void onItemBatchSelected(TrojanConfig config, int pos, boolean checked) {
                mPresenter.selectServer(config, checked);
            }
        });
    }

    private Context getApplicationContext() {
        if (getActivity() != null) {
            return getActivity().getApplicationContext();
        }
        return null;
    }

    @Override
    public void showAddTrojanConfigSuccess() {
        mRootView.post(() ->
                Toast.makeText(getApplicationContext(), R.string.scan_qr_code_success, Toast.LENGTH_SHORT).show());
    }

    @Override
    public void showQRCodeScanError(final String scanContent) {
        mRootView.post(() ->
                Toast.makeText(getApplicationContext(), getString(R.string.scan_qr_code_failed, scanContent), Toast.LENGTH_SHORT).show());
    }

    @Override
    public void askTheWayToScanQRCode() {
        new AlertDialog.Builder(mContext)
                .setItems(R.array.scan_qr_code_choices,
                        (dialog, which) -> mPresenter.gotoScanQRCode(1 == which))
                .show();
    }

    @Override
    public void scanQRCodeFromCamera() {
        if (PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(mContext, Manifest.permission.CAMERA)) {
            scanQRCodeGotResultStartActivityLaunch.launch(ScanQRCodeActivity.create(mContext, false));
        } else {
            scanQRCodeRequestPermissionStartActivityLaunch.launch(Manifest.permission.CAMERA);
        }
    }

    @Override
    public void scanQRCodeFromGallery() {
        scanQRCodeGotResultStartActivityLaunch.launch(ScanQRCodeActivity.create(mContext, true));
    }

    @Override
    public void selectServerConfig(TrojanConfig config) {
        FragmentActivity activity = getActivity();
        if (activity != null) {
            Intent intent = new Intent();
            intent.putExtra(KEY_TROJAN_CONFIG, config);
            activity.setResult(RESULT_OK, intent);
            activity.finish();
        }
    }

    private SubscribeSettingDialog mSubscribeSettingDialog;

    @Override
    public void showSubscribeSettings(String url) {
        if (mSubscribeSettingDialog == null) {
            mSubscribeSettingDialog = new SubscribeSettingDialog(mContext);
            mSubscribeSettingDialog.setOnButtonClickListener(new SubscribeSettingDialog.OnButtonClickListener() {
                @Override
                public void onConfirm(String url) {
                    mPresenter.saveSubscribeSettings(url);
                    mPresenter.hideSubscribeSettings();
                }

                @Override
                public void onCancel() {
                    mPresenter.hideSubscribeSettings();
                }
            });
        }
        mSubscribeSettingDialog.setSubscribeUrl(url);
        mSubscribeSettingDialog.show();
    }

    @Override
    public void dismissSubscribeSettings() {
        if (mSubscribeSettingDialog != null && mSubscribeSettingDialog.isShowing()) {
            mSubscribeSettingDialog.dismiss();
        }
    }

    @Override
    public void showSubscribeUpdateSuccess() {
        SnackbarUtils.showTextShort(mRootView, R.string.subscribe_servers_success);
    }

    @Override
    public void showSubscribeUpdateFailed() {
        SnackbarUtils.showTextShort(mRootView, R.string.subscribe_servers_failed);
    }

    @Override
    public void showServerListBatchOperation() {
        enableBatchOperationMode(true);
    }

    @Override
    public void hideServerListBatchOperation() {
        enableBatchOperationMode(false);
        mServerListAdapter.setAllSelected(false);
    }

    private void enableBatchOperationMode(boolean enable) {
        mBatchOperationMode = enable;
        requireActivity().invalidateOptionsMenu();
        mServerListAdapter.setBatchDeleteMode(enable);
        mItemTouchHelper.attachToRecyclerView(enable ? binding.serverListRv : null);
    }

    @Override
    public void showBatchDeletionSuccess() {
        SnackbarUtils.showTextShort(mRootView, R.string.batch_delete_server_list_success);
    }

    @Override
    public void showLoading() {
        if (mLoadingDialog == null) {
            mLoadingDialog = new LoadingDialog(mContext);
            mLoadingDialog.setCancelable(false);
            mLoadingDialog.setCanceledOnTouchOutside(false);
        }
        mLoadingDialog.show();
    }

    @Override
    public void dismissLoading() {
        if (mLoadingDialog != null && mLoadingDialog.isShowing()) {
            mLoadingDialog.dismiss();
        }
    }

    @Override
    public void batchDelete(Set<TrojanConfig> configList) {
        mServerListAdapter.deleteServers(configList);
    }

    @Override
    public void selectAllServers() {
        mServerListAdapter.setAllSelected(true);
    }

    @Override
    public void deselectAllServers() {
        mServerListAdapter.setAllSelected(false);
    }

    @Override
    public void showImportFileDescription() {
        mImportConfigDialog = new AlertDialog.Builder(mContext).setTitle(R.string.common_alert)
                .setMessage(R.string.server_list_import_file_desc)
                .setPositiveButton(R.string.common_confirm, (dialog, which) -> mPresenter.importConfigFromFile())
                .setNegativeButton(R.string.common_cancel, (dialog, which) -> mPresenter.hideImportFileDescription())
                .create();
        mImportConfigDialog.show();
    }

    @Override
    public void dismissImportFileDescription() {
        if (mImportConfigDialog != null && mImportConfigDialog.isShowing()) {
            mImportConfigDialog.dismiss();
            mImportConfigDialog = null;
        }
    }

    @Override
    public void openFileChooser() {
        importConfigStartActivityLaunch.launch("*/*");
    }

    @Override
    public void showExportServerListSuccess() {
        mRootView.post(() ->
                Toast.makeText(getApplicationContext(), getString(R.string.export_server_list_success, Globals.getIgniterExportPath()), Toast.LENGTH_LONG).show());
    }

    @Override
    public void showExportServerListFailure() {
        mRootView.post(() ->
                Toast.makeText(getApplicationContext(), getString(R.string.export_server_list_error), Toast.LENGTH_SHORT).show());
    }

    @Override
    public void showServerConfigList(final List<TrojanConfig> configs) {
        mRootView.post(() -> mServerListAdapter.replaceData(configs));
    }

    @Override
    public void removeServerConfig(TrojanConfig config, final int pos) {
        mRootView.post(() -> mServerListAdapter.removeItemOnPosition(pos));
    }

    @Override
    public void setPresenter(ServerListContract.Presenter presenter) {
        mPresenter = presenter;
    }

    @Override
    public void setPingServerDelayTime(TrojanConfig config, float timeout) {
        mServerListAdapter.setPingServerDelayTime(config, timeout);
    }
}
