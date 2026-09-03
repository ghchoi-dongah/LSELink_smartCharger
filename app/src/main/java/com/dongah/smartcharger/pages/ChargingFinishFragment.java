package com.dongah.smartcharger.pages;

import android.annotation.SuppressLint;
import android.media.MediaPlayer;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.dongah.smartcharger.MainActivity;
import com.dongah.smartcharger.R;
import com.dongah.smartcharger.basefunction.ChargingCurrentData;
import com.dongah.smartcharger.basefunction.PaymentType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.util.Objects;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ChargingFinishFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ChargingFinishFragment extends Fragment implements View.OnClickListener {
    private static final Logger logger = LoggerFactory.getLogger(ChargingFinishFragment.class);

    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    private static final long UI_CHECK_INTERVAL_MS = 3 * 60 * 1000; // 3분
    Button btnCheck;
    TextView textViewSocValue, textViewChargingAmtValue, textViewChargingTimeValue,
            textViewLimitSocValue,txtChargePay, txtPowerUnitPrice;
    ImageView imageViewSoc;

    MediaPlayer mediaPlayer;
    Handler uiCheckHandler;
    ChargingCurrentData chargingCurrentData;
    DecimalFormat payFormatter = new DecimalFormat("#,###,##0");
    DecimalFormat powerFormatter = new DecimalFormat("#,###,##0.00");


    public ChargingFinishFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment ChargingFinishFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static ChargingFinishFragment newInstance(String param1, String param2) {
        ChargingFinishFragment fragment = new ChargingFinishFragment();
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
        View view = inflater.inflate(R.layout.fragment_charging_finish, container, false);
        chargingCurrentData = ((MainActivity) MainActivity.mContext).getChargingCurrentData();
        btnCheck = view.findViewById(R.id.btnCheck);
        btnCheck.setOnClickListener(this);
        textViewSocValue = view.findViewById(R.id.textViewSocValue);
        textViewChargingAmtValue = view.findViewById(R.id.textViewChargingAmtValue);
        textViewChargingTimeValue = view.findViewById(R.id.textViewChargingTimeValue);
        textViewLimitSocValue = view.findViewById(R.id.textViewLimitSocValue);
        txtChargePay = view.findViewById(R.id.txtChargePay);
        txtPowerUnitPrice = view.findViewById(R.id.txtPowerUnitPrice);
        imageViewSoc = view.findViewById(R.id.imageViewSoc);
        return view;
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        try {
            mediaPlayer();

            if (chargingCurrentData.getSoc() == 0) {
                textViewSocValue.setVisibility(View.INVISIBLE);
                imageViewSoc.setVisibility(View.INVISIBLE);
                textViewLimitSocValue.setVisibility(View.INVISIBLE);
            } else {
                textViewSocValue.setVisibility(View.VISIBLE);
                imageViewSoc.setVisibility(View.VISIBLE);
                textViewLimitSocValue.setVisibility(View.VISIBLE);
            }

            // unplug check 후 초기 화면
            uiCheckHandler = new Handler();
            uiCheckHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!((MainActivity) MainActivity.mContext).getControlBoard().getRxData().isCsPilot()) {
                        ((MainActivity) MainActivity.mContext).getClassUiProcess().onHome();
                    }
                    uiCheckHandler.postDelayed(this, UI_CHECK_INTERVAL_MS);
                }
            }, UI_CHECK_INTERVAL_MS);

            // charging finish info
            ((MainActivity) MainActivity.mContext).runOnUiThread(new Runnable() {
                @SuppressLint("SetTextI18n")
                @Override
                public void run() {
                    textViewSocValue.setText(chargingCurrentData.getSoc() == 0 ? "" : chargingCurrentData.getSoc() + "%");
                    textViewLimitSocValue.setText("목표 충전율: " +chargingCurrentData.getTargetSoc() + "%");
                    textViewChargingAmtValue.setText(powerFormatter.format(chargingCurrentData.getPowerMeterUse() * 0.001) + "kWh");
                    textViewChargingTimeValue.setText(chargingCurrentData.getChargingUseTime());
                    txtChargePay.setText(payFormatter.format(chargingCurrentData.getPowerMeterUsePay()) + "원") ;
//                    txtPowerUnitPrice.setText(payFormatter.format((long) chargingCurrentData.getPowerUnitPrice()) + "원");
                    txtPowerUnitPrice.setText(chargingCurrentData.getPowerUnitPrice() + "원");
                }
            });
        } catch (Exception e) {
            logger.error("onViewCreated error : {}", e.getMessage(), e);
        }
    }


    @Override
    public void onClick(View v) {
        if (!isAdded()) return;

        if (Objects.equals(v.getId(), R.id.btnCheck)) {
            ((MainActivity) MainActivity.mContext).getClassUiProcess().onHome();
        }
    }

    private void mediaPlayer() {
        releasePlayer();

        try {
            mediaPlayer = MediaPlayer.create(requireContext(), R.raw.chargingfinsih);
            mediaPlayer.setOnCompletionListener(me -> releasePlayer());
            mediaPlayer.start();
        } catch (Exception e) {
            logger.error("mediaPlayer error : {}", e.getMessage());
        }
    }

    private void releasePlayer() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.release();
            } catch (Exception e) {
                logger.error("releasePlayer error : {}", e.getMessage());
            }
            mediaPlayer = null;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        try {
            if (uiCheckHandler != null) {
                uiCheckHandler.removeCallbacksAndMessages(null);
                uiCheckHandler = null;
            }
        } catch (Exception e) {
            logger.error("onDestroyView error : {}", e.getMessage(), e);
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }
}