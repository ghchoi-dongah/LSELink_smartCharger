package com.dongah.smartcharger.pages;

import android.annotation.SuppressLint;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.dongah.smartcharger.MainActivity;
import com.dongah.smartcharger.R;
import com.dongah.smartcharger.basefunction.ChargerConfiguration;
import com.dongah.smartcharger.basefunction.ChargingCurrentData;
import com.dongah.smartcharger.basefunction.GlobalVariables;
import com.dongah.smartcharger.basefunction.PaymentType;
import com.dongah.smartcharger.basefunction.UiSeq;
import com.dongah.smartcharger.controlboard.TxData;
import com.dongah.smartcharger.plc.request.StopAllRequest;
import com.dongah.smartcharger.utils.SharedModel;
import com.dongah.smartcharger.websocket.ocpp.utilities.ZonedDateTimeConvert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.util.Date;
import java.util.Objects;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ChargingFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ChargingFragment extends Fragment implements View.OnClickListener {
    private static final Logger logger = LoggerFactory.getLogger(ChargingFragment.class);

    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    Button btnChargingStop;
    TextView textViewSocValue, textViewLimitSocValue, textViewChargingAmtValue, txtChargePay, textViewChargingTimeValue, txtPowerUnitPrice;

    MediaPlayer mediaPlayer;
    SharedModel sharedModel;
    String[] requestStrings = new String[1];
    Handler uiUpdateHandler;
    MainActivity activity;
    ChargingCurrentData chargingCurrentData;
    ChargerConfiguration chargerConfiguration;
    TxData txData;

    Date startTime = null, useTime = null;
    DecimalFormat payFormatter = new DecimalFormat("#,###,##0");
    DecimalFormat powerFormatter = new DecimalFormat("#,###,##0.00");
    ZonedDateTimeConvert zonedDateTimeConvert = new ZonedDateTimeConvert();

    public ChargingFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment ChargingFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static ChargingFragment newInstance(String param1, String param2) {
        ChargingFragment fragment = new ChargingFragment();
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
        View view = inflater.inflate(R.layout.fragment_charging, container, false);
        activity = ((MainActivity) MainActivity.mContext);
        chargingCurrentData = activity.getChargingCurrentData();
        chargerConfiguration = activity.getChargerConfiguration();
        txData = activity.getControlBoard().getTxData();
        btnChargingStop = view.findViewById(R.id.btnChargingStop);
        btnChargingStop.setOnClickListener(this);
        textViewSocValue = view.findViewById(R.id.textViewSocValue);
        textViewLimitSocValue = view.findViewById(R.id.textViewLimitSocValue);
        textViewChargingAmtValue = view.findViewById(R.id.textViewChargingAmtValue);
        textViewChargingTimeValue = view.findViewById(R.id.textViewChargingTimeValue);
        txtChargePay = view.findViewById(R.id.txtChargePay);
        txtPowerUnitPrice = view.findViewById(R.id.txtPowerUnitPrice);
        return view;
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        try {
            sharedModel = new ViewModelProvider(requireActivity()).get(SharedModel.class);
            requestStrings[0] = String.valueOf(0);
            sharedModel.setMutableLiveData(requestStrings);
            mediaPlayer();      // media player

            try {
                if (chargingCurrentData.getSoc() == 0) {
                    textViewSocValue.setVisibility(View.INVISIBLE);
                } else {
                    textViewSocValue.setVisibility(View.VISIBLE);
                    textViewSocValue.setText(chargingCurrentData.getSoc() + "%");
                }
                textViewLimitSocValue.setText("목표 충전율: " + chargingCurrentData.getTargetSoc() + "%");
                startTime = zonedDateTimeConvert.doStringDateToDate(chargingCurrentData.getChargingStartTime());
                txtPowerUnitPrice.setText(payFormatter.format((long) chargingCurrentData.getPowerUnitPrice()) + "원");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            onCharging();
        } catch (Exception e) {
            logger.error("onViewCreated error : {}", e.getMessage());
        }
    }

    @Override
    public void onClick(View v) {
        if (Objects.equals(v.getId(), R.id.btnChargingStop)) {
            if (Objects.equals(chargerConfiguration.getOpMode(), 0)) {
                // test mode
                stopCharging();
            } else {
                // server mode
                boolean requireRfCard = !Objects.equals(chargingCurrentData.getPaymentType(), PaymentType.CREDIT) &&
                        chargerConfiguration.isStopConfirm();
                if (requireRfCard) {
                    activity.getFragmentChange().onFragmentChange(UiSeq.MEMBER_CARD, "MEMBER_CARD", null);
                } else {
                    stopCharging();
                }
            }
        }
    }

    private void stopCharging() {
        chargingCurrentData.setUserStop(true);
        txData.setMainMC(false);
        txData.setPwmDuty((short) 100);

        StopAllRequest stopAllRequest = new StopAllRequest((byte) 0x76, (short) 8, (byte) 0x00);

        byte[] report = stopAllRequest.makeStopAllRequest("STOP", (short) 534, (short) 123);
        activity.getPlcModem().onSend(report);
    }

    
    private void onCharging() {
        uiUpdateHandler = new Handler();
        uiUpdateHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                activity.runOnUiThread(new Runnable() {
                     @SuppressLint({"SetTextI18n", "DefaultLocale"})
                     @RequiresApi(api = Build.VERSION_CODES.O)
                     @Override
                     public void run() {
                         try {
                             long diffTime = 0;
                             useTime = zonedDateTimeConvert.doStringDateToDate(zonedDateTimeConvert.getStringCurrentTimeZone());

                             if (useTime != null) {
                                 diffTime = (useTime.getTime() - startTime.getTime()) / 1000;
                                 int hour = (int) diffTime / 3600;
                                 int minute = (int) (diffTime % 3600) / 60;
                                 int second = (int) diffTime % 60;
                                 chargingCurrentData.setChargingTime((int) diffTime);
                                 textViewChargingTimeValue.setText(String.format("%02d", hour) + ":" + String.format("%02d", minute) + ":" + String.format("%02d", second));
                                 chargingCurrentData.setChargingUseTime(textViewChargingTimeValue.getText().toString());
                                 txtChargePay.setText(payFormatter.format((long) chargingCurrentData.getPowerMeterUsePay()) + "원");
                                 textViewChargingAmtValue.setText(powerFormatter.format(chargingCurrentData.getPowerMeterUse() * 0.01) + "kWh");

                                 if (chargingCurrentData.getSoc() == 0) {
                                     textViewSocValue.setVisibility(View.INVISIBLE);
                                 } else {
                                     textViewSocValue.setVisibility(View.VISIBLE);
                                     textViewSocValue.setText(chargingCurrentData.getSoc() + "%");
                                 }
                             }
                         } catch (Exception e) {
                             logger.error("onCharging error : {}", e.getMessage());
                         }
                     }
                 });
                uiUpdateHandler.postDelayed(this, 1000);
            }
        }, 50);
    }
    
    private void mediaPlayer() {
        releasePlayer();
        
        try {
            mediaPlayer = MediaPlayer.create(requireContext(), R.raw.charging);
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
            if (uiUpdateHandler != null) {
                uiUpdateHandler.removeCallbacksAndMessages(null);
                uiUpdateHandler = null;
            }
        } catch (Exception e) {
            logger.error("onDestroyView error : {}", e.getMessage(), e);
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        try {
            requestStrings[0] = String.valueOf(0);
            sharedModel.setMutableLiveData(requestStrings);
        } catch (Exception e) {
            logger.error("onDetach error : {}", e.getMessage());
        }
    }
}