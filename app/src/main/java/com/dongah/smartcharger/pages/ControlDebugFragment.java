package com.dongah.smartcharger.pages;

import android.annotation.SuppressLint;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;

import com.dongah.smartcharger.MainActivity;
import com.dongah.smartcharger.R;
import com.dongah.smartcharger.controlboard.ControlBoard;
import com.dongah.smartcharger.controlboard.ControlBoardListener;
import com.dongah.smartcharger.controlboard.ControlBoardUtil;
import com.dongah.smartcharger.controlboard.ListViewDspAdapter;
import com.dongah.smartcharger.controlboard.RxData;
import com.dongah.smartcharger.controlboard.TxData;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.util.Objects;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ControlDebugFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ControlDebugFragment extends Fragment implements View.OnClickListener, ControlBoardListener {
    private final static Logger logger = LoggerFactory.getLogger(ControlDebugFragment.class);

    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";


    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    Button btnClose;
    DecimalFormat decimalFormat;
    ListView listRx, listTx;
    ListViewDspAdapter listViewRxAdapter, listViewTxAdapter;
    ControlBoard controlBoard;
    ControlBoardUtil controlBoardUtil;

    public ControlDebugFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment ControlDebugFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static ControlDebugFragment newInstance(String param1, String param2) {
        ControlDebugFragment fragment = new ControlDebugFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_control_debug, container, false);
        btnClose = view.findViewById(R.id.btnClose);
        btnClose.setOnClickListener(this);

        decimalFormat = new DecimalFormat("#,###,##0.0#");
        controlBoardUtil = new ControlBoardUtil();

        //** Rx data */
        listRx = (ListView) view.findViewById(R.id.listRx);
        listViewRxAdapter = new ListViewDspAdapter();
        listViewRxAdapter.notifyDataSetChanged();
        listRx.setAdapter(listViewRxAdapter);

        //* Tx data */
        listTx = (ListView) view.findViewById(R.id.listTx);
        listViewTxAdapter = new ListViewDspAdapter();
        listViewTxAdapter.notifyDataSetChanged();
        listTx.setAdapter(listViewTxAdapter);

        controlBoard = ((MainActivity) MainActivity.mContext).getControlBoard();
        controlBoard.setControlBoardListener(this);

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        try {
        } catch (Exception e) {
            logger.error("onViewCreated error : {}", e.getMessage());
        }
    }

    @Override
    public void onClick(View v) {
        if (Objects.equals(v.getId(), R.id.btnClose)) {
            FragmentTransaction transaction = ((MainActivity) MainActivity.mContext).getSupportFragmentManager().beginTransaction();
            EnvironmentFragment environmentFragment = new EnvironmentFragment();
            transaction.replace(R.id.frameFull, environmentFragment);
            transaction.commit();
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        controlBoard.setControlBoardListenerStop();
    }

    @Override
    public void onControlBoardReceive(RxData rxData) {
        try {
            if (getActivity() != null) {
                getActivity().runOnUiThread(new Runnable() {
                    @SuppressLint("DefaultLocale")
                    @Override
                    public void run() {
                        listViewRxAdapter.clearItem();
                        listViewRxAdapter.addItem("RX", "csPilot", String.valueOf(rxData.isCsPilot()));
                        listViewRxAdapter.addItem("RX", "csStart", String.valueOf(rxData.isCsStart()));
                        listViewRxAdapter.addItem("RX", "csStop", String.valueOf(rxData.isCsStop()));
                        listViewRxAdapter.addItem("RX", "csFault", String.valueOf(rxData.isCsFault()));
                        listViewRxAdapter.addItem("RX", "csOVR", String.valueOf(rxData.isCsOVR()));
                        listViewRxAdapter.addItem("RX", "csUVR", String.valueOf(rxData.isCsUVR()));
                        listViewRxAdapter.addItem("RX", "csOCR", String.valueOf(rxData.isCsOCR()));

                        listViewRxAdapter.addItem("RX", "csCPStatus", String.valueOf(rxData.getCsCPStatus()));
                        listViewRxAdapter.addItem("RX", "csPwmDuty", String.valueOf(rxData.getCsPwmDuty()));
                        listViewRxAdapter.addItem("RX", "csCpVoltage", String.valueOf(rxData.getCsCpVoltage() * 0.1));
                        listViewRxAdapter.addItem("RX", "FW Ver", controlBoardUtil.parseVersion(rxData.getCsFirmwareVersion()));
                        listViewRxAdapter.addItem("RX", "csRunCount", String.valueOf(rxData.getCsRunCount()));
                        listViewTxAdapter.addItem("RX", "csEmergency", rxData.isCsEmergency() ? "비상버튼 눌림" : "정상");
                        listViewTxAdapter.addItem("RX", "csMcStatus", rxData.isCsMcStatus() ? "Close" : "Open");
                        listViewTxAdapter.addItem("RX", "uiSequence", rxData.getCsSequenceStatus() == 1 ? "대기" : rxData.getCsSequenceStatus() == 2 ? "충전" : "종료");
                        listViewTxAdapter.addItem("RX", "reserve0", String.valueOf(rxData.getReserve0()));
                        listViewTxAdapter.addItem("RX", "reserve1", String.valueOf(rxData.getReserve1()));

                        listViewTxAdapter.addItem("RX", "voltage", String.valueOf(rxData.getVoltage() * 0.01));
                        listViewTxAdapter.addItem("RX", "current", String.valueOf(rxData.getCurrent() * 0.001));
                        listViewTxAdapter.addItem("RX", "ActiveEnergy", String.valueOf(rxData.getActiveEnergy() * 0.01));
                        listViewTxAdapter.addItem("RX", "ActivePower", String.valueOf(rxData.getActivePower() * 0.1));
                        listViewTxAdapter.addItem("RX", "Frequency", String.valueOf(rxData.getFrequency() * 0.01));

                        ///나중에 PLC 모뎀
                        listViewRxAdapter.notifyDataSetChanged();
                    }
                });
            }
        } catch (Exception e) {
            listViewRxAdapter.clearItem();
            logger.error("onControlBoardReceive error :  {}", e.getMessage());
        }
    }

    @Override
    public void onControlBoardSend(TxData txData) {
        try {
            if (getActivity() != null) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        listViewTxAdapter.clearItem();
                        listViewTxAdapter.addItem("TX", "IsBoardRest", String.valueOf(txData.IsBoardRest));
                        listViewTxAdapter.addItem("TX", "IsMainMC", String.valueOf(txData.IsMainMC));
                        listViewTxAdapter.addItem("TX", "IsCPRelay", String.valueOf(txData.IsCPRelay));
                        listViewTxAdapter.addItem("TX", "pwmDuty", String.valueOf(txData.pwmDuty));
                        listViewTxAdapter.addItem("TX", "uiSequence", txData.uiSequence == 1 ? "대기" : txData.uiSequence == 2 ? "충전" : "종료");
                        listViewTxAdapter.addItem("TX", "runCount", String.valueOf(txData.runCount));
                        listViewTxAdapter.addItem("TX", "powerMeter", String.valueOf(txData.powerMeter));
                        listViewTxAdapter.addItem("TX", "highPowerMeter", String.valueOf(txData.highPowerMeter));
                        listViewTxAdapter.addItem("TX", "lowPowerMeter", String.valueOf(txData.lowPowerMeter));
                        listViewTxAdapter.addItem("TX", "outVoltage", String.valueOf(txData.outVoltage));
                        listViewTxAdapter.addItem("TX", "outCurrent", String.valueOf(txData.outCurrent));
                        listViewTxAdapter.notifyDataSetChanged();
                    }
                });
            }
        } catch (Exception e) {
            listViewRxAdapter.clearItem();
            logger.error("onControlBoardSend error :  {}", e.getMessage());
        }
    }
}