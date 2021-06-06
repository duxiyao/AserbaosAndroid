package com.aserbao.aserbaosandroid.ui.customView.selector.rv;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import com.aserbao.aserbaosandroid.R;
import com.aserbao.aserbaosandroid.ui.customView.selector.FeatureConfigSelVH;
import com.aserbao.aserbaosandroid.ui.customView.selector.beans.RoleConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * description:
 * Created by aserbao on 2018/1/25.
 * 顶部导航栏
 */
public class SelRVBottomContainerAdapter extends RecyclerView.Adapter<FeatureConfigSelVH> {


    private Context mContext;
    protected List<RoleConfig> mRoleConfig = new ArrayList<>();
    private ItemClickerListener mItemClickerListener;


    public SelRVBottomContainerAdapter(Context mContext, List<RoleConfig> roleConfig, ItemClickerListener itemClickerListener) {
        this.mContext = mContext;
        mRoleConfig = roleConfig;
        this.mItemClickerListener = itemClickerListener;
    }



    @Override
    public FeatureConfigSelVH onCreateViewHolder(ViewGroup parent, int viewType) {
        View view;
        view = LayoutInflater.from(mContext).inflate(R.layout.rv_item_color_selector_ll, parent, false);
        return new FeatureConfigSelVH(view);
    }

    @Override
    public void onBindViewHolder(FeatureConfigSelVH holder, int position) {
        if(position < mRoleConfig.size()){
            RoleConfig roleConfig = mRoleConfig.get(position);
            holder.setDataSource(mContext,roleConfig,position);
        }
    }

    @Override
    public int getItemCount() {
        int ret = 0;
        if (mRoleConfig.size() > 0) {
            ret = mRoleConfig.size();
        }
        return ret;
    }


    public interface ItemClickerListener{
        void itemClick(RoleConfig beans, int position);
    }

}
