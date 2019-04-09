package io.helix.android.ui.settings_rates;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import org.helixj.core.Peer;

import java.util.List;

import io.helix.android.helixApplication;
import io.helix.android.R;
import io.helix.android.rate.db.helixRate;
import io.helix.android.ui.base.BaseRecyclerFragment;
import io.helix.android.ui.base.tools.adapter.BaseRecyclerAdapter;
import io.helix.android.ui.base.tools.adapter.BaseRecyclerViewHolder;
import io.helix.android.ui.base.tools.adapter.ListItemListeners;
import io.helix.android.ui.settings_network_activity.NetworkViewHolder;

/**
 * Created by furszy on 7/2/17.
 */

public class RatesFragment extends BaseRecyclerFragment<helixRate> implements ListItemListeners<helixRate> {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        setEmptyText("No rate available");
        setEmptyTextColor(Color.parseColor("#cccccc"));
        return view;
    }

    @Override
    protected List<helixRate> onLoading() {
        return helixModule.listRates();
    }

    @Override
    protected BaseRecyclerAdapter<helixRate, ? extends helixRateHolder> initAdapter() {
        BaseRecyclerAdapter<helixRate, helixRateHolder> adapter = new BaseRecyclerAdapter<helixRate, helixRateHolder>(getActivity()) {
            @Override
            protected helixRateHolder createHolder(View itemView, int type) {
                return new helixRateHolder(itemView,type);
            }

            @Override
            protected int getCardViewResource(int type) {
                return R.layout.rate_row;
            }

            @Override
            protected void bindHolder(helixRateHolder holder, helixRate data, int position) {
                holder.txt_name.setText(data.getCode());
                if (list.get(0).getCode().equals(data.getCode()))
                    holder.view_line.setVisibility(View.GONE);
            }
        };
        adapter.setListEventListener(this);
        return adapter;
    }

    @Override
    public void onItemClickListener(helixRate data, int position) {
        helixApplication.getAppConf().setSelectedRateCoin(data.getCode());
        Toast.makeText(getActivity(),R.string.rate_selected,Toast.LENGTH_SHORT).show();
        getActivity().onBackPressed();
    }

    @Override
    public void onLongItemClickListener(helixRate data, int position) {

    }

    private  class helixRateHolder extends BaseRecyclerViewHolder{

        private TextView txt_name;
        private View view_line;

        protected helixRateHolder(View itemView, int holderType) {
            super(itemView, holderType);
            txt_name = (TextView) itemView.findViewById(R.id.txt_name);
            view_line = itemView.findViewById(R.id.view_line);
        }
    }
}
