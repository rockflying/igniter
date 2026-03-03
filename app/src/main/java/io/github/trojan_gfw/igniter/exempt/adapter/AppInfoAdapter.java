package io.github.trojan_gfw.igniter.exempt.adapter;

import android.content.res.Resources;
import android.graphics.Rect;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.widget.TextViewCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.trojan_gfw.igniter.R;
import io.github.trojan_gfw.igniter.exempt.data.AppInfo;

public class AppInfoAdapter extends RecyclerView.Adapter<AppInfoAdapter.ViewHolder> {
    private final List<AppInfo> mData = new ArrayList<>();
    private OnItemOperationListener mOnItemOperationListener;
    private final Rect mIconBound = new Rect();

    public AppInfoAdapter() {
        super();
        final int size = Resources.getSystem().getDimensionPixelSize(android.R.dimen.app_icon_size);
        mIconBound.right = size;
        mIconBound.bottom = size;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.item_app_info, viewGroup, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, int i) {
        if (i != RecyclerView.NO_POSITION) {
            viewHolder.bindData(mData.get(i));
        }
    }

    public void removeData(int position) {
        mData.remove(position);
        notifyItemRemoved(position);
    }

    public void refreshData(List<AppInfo> newData) {
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new AppInfoDiffCallback(mData, newData));
        mData.clear();
        mData.addAll(newData);
        diffResult.dispatchUpdatesTo(this);
    }

    @Override
    public int getItemCount() {
        return mData.size();
    }

    public void setOnItemOperationListener(OnItemOperationListener onItemOperationListener) {
        mOnItemOperationListener = onItemOperationListener;
    }

    public interface OnItemOperationListener {
        void onToggle(boolean exempt, AppInfo appInfo, int position);
    }

    class ViewHolder extends RecyclerView.ViewHolder implements CompoundButton.OnCheckedChangeListener {
        private TextView mNameTv;
        private SwitchMaterial mExemptSwitch;
        private AppInfo mCurrentInfo;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            mNameTv = itemView.findViewById(R.id.appNameTv);
            TextViewCompat.setAutoSizeTextTypeWithDefaults(mNameTv, TextViewCompat.AUTO_SIZE_TEXT_TYPE_NONE);
            mExemptSwitch = itemView.findViewById(R.id.appExemptSwitch);
        }

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (mOnItemOperationListener != null) {
                mOnItemOperationListener.onToggle(isChecked, mCurrentInfo, getBindingAdapterPosition());
            }
        }

        void bindData(AppInfo appInfo) {
            mCurrentInfo = appInfo;
            mNameTv.setText(appInfo.getAppName());
            appInfo.getIcon().setBounds(mIconBound);
            mNameTv.setCompoundDrawables(appInfo.getIcon(), null, null, null);
            mExemptSwitch.setOnCheckedChangeListener(null);
            mExemptSwitch.setChecked(appInfo.isExempt());
            mExemptSwitch.setOnCheckedChangeListener(this);
        }
    }

    private static class AppInfoDiffCallback extends DiffUtil.Callback {
        private final List<AppInfo> oldList;
        private final List<AppInfo> newList;

        AppInfoDiffCallback(List<AppInfo> oldList, List<AppInfo> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override
        public int getOldListSize() {
            return oldList.size();
        }

        @Override
        public int getNewListSize() {
            return newList.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            // 使用 packageName 作为唯一标识
            return Objects.equals(oldList.get(oldItemPosition).getPackageName(),
                    newList.get(newItemPosition).getPackageName());
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            AppInfo oldItem = oldList.get(oldItemPosition);
            AppInfo newItem = newList.get(newItemPosition);
            // 比较显示内容是否相同
            return Objects.equals(oldItem.getAppName(), newItem.getAppName())
                    && oldItem.isExempt() == newItem.isExempt();
        }
    }
}
