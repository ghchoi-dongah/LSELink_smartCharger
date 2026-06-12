package com.dongah.smartcharger.pages;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.dongah.smartcharger.MainActivity;
import com.dongah.smartcharger.R;
import com.dongah.smartcharger.basefunction.ChargerConfiguration;
import com.dongah.smartcharger.basefunction.ChargingCurrentData;
import com.dongah.smartcharger.basefunction.ClassUiProcess;
import com.dongah.smartcharger.basefunction.FragmentChange;
import com.dongah.smartcharger.basefunction.GlobalVariables;
import com.dongah.smartcharger.basefunction.UiSeq;
import com.dongah.smartcharger.controlboard.RxData;
import com.dongah.smartcharger.controlboard.TxData;
import com.dongah.smartcharger.utils.BitUtilities;
import com.dongah.smartcharger.utils.SharedModel;
import com.dongah.smartcharger.websocket.ocpp.core.ChargePointErrorCode;
import com.dongah.smartcharger.websocket.ocpp.core.ChargePointStatus;
import com.dongah.smartcharger.websocket.ocpp.core.Reason;
import com.dongah.smartcharger.websocket.socket.SocketReceiveMessage;
import com.dongah.smartcharger.websocket.socket.SocketState;
import com.dongah.smartcharger.websocket.socket.handler.handlersend.AuthorizeReq;
import com.dongah.smartcharger.websocket.socket.handler.handlersend.StatusNotificationReq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ConnectorCheckFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ConnectorCheckFragment extends Fragment implements View.OnClickListener {
    private static final Logger logger = LoggerFactory.getLogger(ConnectorCheckFragment.class);

    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";
    private static final String CHANNEL = "CHANNEL";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;
    private int mChannel;


    int cnt = 0;
    boolean isFlag = false, isFlagAuthorize = true;
    TextView textViewConnectorCheckMessage, textViewFailed, textViewConnector;
    ImageView imageViewLoading, imageViewConnectionFailed;
    AnimationDrawable animationDrawable;
    ObjectAnimator fadeAnimator;
    RxData rxData;
    TxData txData;
    Handler countHandler;
    Runnable countRunnable;
    SharedModel sharedModel;
    String[] requestStrings = new String[1];
    MainActivity activity;
    ClassUiProcess classUiProcess;
    ChargerConfiguration chargerConfiguration;
    ChargingCurrentData chargingCurrentData;
    FragmentChange fragmentChange;

    public ConnectorCheckFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment ConnectorCheckFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static ConnectorCheckFragment newInstance(String param1, String param2) {
        ConnectorCheckFragment fragment = new ConnectorCheckFragment();
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
            mChannel = getArguments().getInt(CHANNEL);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_connector_check, container, false);
        view.setOnClickListener(this);
        textViewConnectorCheckMessage = view.findViewById(R.id.textViewConnectorCheckMessage);
        imageViewLoading = view.findViewById(R.id.imageViewLoading);
        imageViewLoading.setBackgroundResource(R.drawable.ani_loading);
        animationDrawable = (AnimationDrawable) imageViewLoading.getBackground();
        imageViewConnectionFailed = view.findViewById(R.id.imageViewConnectionFailed);
        textViewFailed = view.findViewById(R.id.textViewFailed);
        textViewConnector = view.findViewById(R.id.textViewConnector);

        // textViewFailed animation
        fadeAnimator = ObjectAnimator.ofFloat(textViewFailed, "alpha", 1f, 0.2f);
        fadeAnimator.setDuration(1000);
        fadeAnimator.setRepeatCount(ValueAnimator.INFINITE);
        fadeAnimator.setRepeatMode(ValueAnimator.REVERSE);
        fadeAnimator.setInterpolator(new AccelerateDecelerateInterpolator());

        activity = ((MainActivity) MainActivity.mContext);
        classUiProcess = activity.getClassUiProcess();
        chargerConfiguration = activity.getChargerConfiguration();
        chargingCurrentData = activity.getChargingCurrentData();
        fragmentChange = activity.getFragmentChange();
        return view;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        try {
            sharedModel = new ViewModelProvider(requireActivity()).get(SharedModel.class);
            requestStrings[0] = String.valueOf(0);
            sharedModel.setMutableLiveData(requestStrings);
            rxData = activity.getControlBoard().getRxData();
            txData = activity.getControlBoard().getTxData();
            cnt = 0;
            isFlag = false;
            isFlagAuthorize = true;
            animationDrawable.start();

            // connection time out
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    countHandler = new Handler();
                    countRunnable = new Runnable() {
                        @Override
                        public void run() {
                            cnt++;
                            if (cnt >= GlobalVariables.getConnectionTimeOut()) {
                                // 충전기 종료
                                countHandler.removeCallbacks(countRunnable);

                                // preparing
                                if (Objects.equals(chargingCurrentData.getChargePointStatus(), ChargePointStatus.Preparing) &&
                                        Objects.equals(chargerConfiguration.getOpMode(), 1) &&
                                        !((MainActivity) MainActivity.mContext).getControlBoard().getRxData().isCsPilot()) {
                                    chargingCurrentData.setChargePointStatus(ChargePointStatus.Available);
                                    chargingCurrentData.setChargePointErrorCode(ChargePointErrorCode.NoError);

                                    // StatusNotification
                                    StatusNotificationReq statusNotificationReq = new StatusNotificationReq(chargingCurrentData.getConnectorId());
                                    statusNotificationReq.sendStatusNotification();
                                }

                                // 통신 실패
                                classUiProcess.setUiSeq(UiSeq.CONNECTION_FAILED);
                                fragmentChange.onFragmentChange(UiSeq.CONNECTION_FAILED, "CONNECTION_FAILED", null);
                            } else {
                                countHandler.postDelayed(countRunnable, 1000);
                            }

                            // connecting wait
                            if (rxData.isCsPilot()) {
                                if (textViewConnectorCheckMessage.getTag() == null || !(boolean) textViewConnectorCheckMessage.getTag()) {
                                    textViewConnectorCheckMessage.setText(R.string.EVCheckMessage);
                                    textViewConnectorCheckMessage.setTag(true);
                                }

                                // 서버 모드 && MAC Address 인증 모드(authType = 'M')
                                // Authorize(Mac Address) send, 1회 시도
                                if (Objects.equals(chargerConfiguration.getOpMode(), 1) &&
                                        Objects.equals(chargingCurrentData.getAuthType(), "M") &&
                                        isFlagAuthorize) {
                                    macAuthorize(); // Authorize(MAC Address)
                                }
                            }
                        }
                    };
                    countHandler.postDelayed(countRunnable, 1000);
                }
            });
        } catch (Exception e) {
            logger.error("ConnectorCheckFragment onViewCreated error : {}", e.getMessage());
        }
    }

    @Override
    public void onClick(View v) {
        try {
            return;
        } catch (Exception e) {
            logger.error("ConnectorCheckFragment onClick error : {}", e.getMessage());
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void macAuthorize() {
        String[] idTagInfo;
        UiSeq uiSeq = classUiProcess.getUiSeq();
        SocketReceiveMessage socketReceiveMessage = ((MainActivity) MainActivity.mContext).getSocketReceiveMessage();

//        String evccId = BitUtilities.toHexString(rxData.getCsmVehicleEvccId());
        String evccId = "000000000000";
        Log.d("ConnectorCheckFragment", "mac address : " + evccId);
        chargingCurrentData.setIdTag(evccId);
//        chargingCurrentData.setIdTag("1364747EE708"); // failed
//        chargingCurrentData.setIdTag("1364747EE704"); // success

        if (chargingCurrentData.getIdTag().equals("000000000000")) return;
        isFlagAuthorize = false; // MAC Authorize 1회 시도
        chargingCurrentData.setIdTag("C" + chargingCurrentData.getIdTag());

        // isLocalPreAuthorize == true : local authorization list 에서 사용자 인증
        // isLocalPreAuthorize: 사전 로컬 인증 모드
        if (GlobalVariables.isLocalPreAuthorize()) {
            // local authorization enabled --> local 인증
            idTagInfo = socketReceiveMessage.getLocalAuthorizationListStrings(uiSeq == UiSeq.CHARGING ? chargingCurrentData.getIdTagStop() : chargingCurrentData.getIdTag());
            if (Objects.equals(UiSeq.CHARGING, uiSeq)) {
                if (Objects.equals(chargingCurrentData.getParentIdTag(), idTagInfo[1]) ||
                        Objects.equals(chargingCurrentData.getIdTag(), chargingCurrentData.getIdTagStop())) {
                    classUiProcess.setUiSeq(UiSeq.FINISH_WAIT);
                    ((MainActivity) MainActivity.mContext).getFragmentChange().onFragmentChange(UiSeq.FINISH_WAIT, "FINISH_WAIT", null);
                } else  {
                    classUiProcess.setUiSeq(UiSeq.CHARGING);
                    fragmentChange.onFragmentChange(UiSeq.CHARGING, "CHARGING", null);
                }
            } else {
                if (!Objects.equals(chargingCurrentData.getChargePointStatus(), ChargePointStatus.Preparing) &&
                        Objects.equals(chargerConfiguration.getOpMode(), 1)) {
                    chargingCurrentData.setChargePointStatus(ChargePointStatus.Preparing);
                    StatusNotificationReq statusNotificationReq = new StatusNotificationReq(chargingCurrentData.getConnectorId());
                    statusNotificationReq.sendStatusNotification();
                }

                if (Objects.equals(idTagInfo[0], chargingCurrentData.getIdTag())) {
                    chargingCurrentData.setAuthorizeResult(true);
                    chargingCurrentData.setParentIdTag(idTagInfo[1]);
                } else if (Objects.equals(idTagInfo[0], "notFound")) {
                    AuthorizeReq authorizeReq = new AuthorizeReq(chargingCurrentData.getConnectorId());
                    authorizeReq.sendAuthorize(chargingCurrentData.getIdTag());
                } else {
                    // 인증 실패
                    ((MainActivity) MainActivity.mContext).getChargingCurrentData().setAuthorizeResult(false);
                    authorizedFailed();
                    RxData rxData = ((MainActivity) MainActivity.mContext).getControlBoard().getRxData();
                    if (!rxData.isCsPilot() && Objects.equals(chargerConfiguration.getOpMode(), 1)) {
                        chargingCurrentData.setChargePointStatus(ChargePointStatus.Available);
                        StatusNotificationReq statusNotificationReq = new StatusNotificationReq(chargingCurrentData.getConnectorId());
                        statusNotificationReq.sendStatusNotification();
                    }
                }
            }
        } else {
            // central system send
            SocketState state = socketReceiveMessage.getSocket().getState();
            if (state == SocketState.OPEN) {
                if (Objects.equals(UiSeq.CHARGING, uiSeq) && Objects.equals(chargingCurrentData.getIdTag(), chargingCurrentData.getIdTagStop())) {
                    fragmentChange.onFragmentChange(UiSeq.FINISH_WAIT, "FINISH_WAIT", null);
                } else {
                    if (chargingCurrentData.getChargePointStatus() == ChargePointStatus.Reserved) {
                        if (!Objects.equals(chargingCurrentData.getResIdTag(), chargingCurrentData.getIdTag())) {
                            Toast.makeText(getActivity(), "예약한 IdTag가 틀립니다. ", Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }
                    AuthorizeReq authorizeReq = new AuthorizeReq(chargingCurrentData.getConnectorId());
                    authorizeReq.sendAuthorize(chargingCurrentData.getIdTag());
                }
            } else {
                // 서버와 연결이 안된 경우
                // isLocalAuthorizeOffline: 서버 연결이 끊겼을 때 오프라인 로컬 인증 허용 여부
                if (GlobalVariables.isLocalAuthorizeOffline()) {
                    // local authorization enabled --> local 인증
                    idTagInfo = socketReceiveMessage.getLocalAuthorizationListStrings(uiSeq == UiSeq.CHARGING ? chargingCurrentData.getIdTagStop() : chargingCurrentData.getIdTag());
                    if (Objects.equals(UiSeq.CHARGING, uiSeq)) {
                        if (Objects.equals(chargingCurrentData.getParentIdTag(), idTagInfo[1]) ||
                                Objects.equals(chargingCurrentData.getIdTag(), chargingCurrentData.getIdTagStop())) {
                            classUiProcess.setUiSeq(UiSeq.FINISH_WAIT);
                            fragmentChange.onFragmentChange(UiSeq.FINISH_WAIT, "FINISH_WAIT", null);
                        } else {
                            classUiProcess.setUiSeq(UiSeq.CHARGING);
                            fragmentChange.onFragmentChange(UiSeq.CHARGING, "CHARGING", null);
                        }
                    } else {
                        // isAllowOfflineTxForUnknownId: 오프라인에서 미등록 IdTag도 거래 허용
                        if (Objects.equals(idTagInfo[0], chargingCurrentData.getIdTag()) || GlobalVariables.isAllowOfflineTxForUnknownId() ||
                                GlobalVariables.isStopTransactionOnInvalidId()) {
                            chargingCurrentData.setAuthorizeResult(true);
                            chargingCurrentData.setParentIdTag(Objects.equals(idTagInfo[1], "") ? "미지원" : idTagInfo[1]);

                            AuthorizeReq authorizeReq = new AuthorizeReq(chargingCurrentData.getConnectorId());
                            authorizeReq.sendAuthorize(chargingCurrentData.getIdTag());
                            
                            chargingCurrentData.setChargePointStatus(ChargePointStatus.Preparing);
                            StatusNotificationReq statusNotificationReq = new StatusNotificationReq(chargingCurrentData.getConnectorId());
                            statusNotificationReq.sendStatusNotification();

                            // isStopTransactionOnInvalidId: 미등록 IdTag로 시작했으면 나중에 중단 사유 세팅
                            chargingCurrentData.setStopReason(!Objects.equals(idTagInfo[0], chargingCurrentData.getIdTag()) &&
                                    GlobalVariables.isStopTransactionOnInvalidId() ? Reason.DeAuthorized : chargingCurrentData.getStopReason());
                        } else {
                            // 인증 실패
                            authorizedFailed();
                        }
                    }
                } else {
                    Toast.makeText(getActivity(), "서버와 통신 DISCONNECT!!! 인증 실패. ", Toast.LENGTH_SHORT).show();
                    if (Objects.equals(UiSeq.CHARGING, uiSeq)) {
                        classUiProcess.setUiSeq(UiSeq.CHARGING);
                        fragmentChange.onFragmentChange(UiSeq.CHARGING, "CHARGING", null);
                    } else {
                        authorizedFailed();
                    }
                }
            }
        }
    }

    private void authorizedFailed() {
        try {
            // charging stop
            activity.getControlBoard().getTxData().setUiSequence((short) 3);

            // member check failed fragment
            countHandler.removeCallbacks(countRunnable);
            classUiProcess.setUiSeq(UiSeq.MEMBER_CHECK_FAILED);
            fragmentChange.onFragmentChange(UiSeq.MEMBER_CHECK_FAILED, "MEMBER_CHECK_FAILED", null);
        } catch (Exception e) {
            logger.error("ConnectorCheckFragment authorizedFailed error : {}", e.getMessage());
        }
    }

    @Override
    public void onDestroyView() {
        try {
            if (fadeAnimator != null) {
                fadeAnimator.cancel();
                fadeAnimator = null;
            }

            if (animationDrawable != null) {
                animationDrawable.stop();
            }

            if (imageViewLoading != null) {
                Drawable bg = imageViewLoading.getBackground();
                if (bg instanceof AnimationDrawable) {
                    ((AnimationDrawable) bg).stop();
                }
                imageViewLoading.setBackground(null);
            }

            if (countHandler != null) {
                countHandler.removeCallbacksAndMessages(null);
                countHandler = null;
            }
            countRunnable = null;

        } catch (Exception e) {
            logger.error("ConnectorCheckFragment onDestroyView error : {}", e.getMessage());
        }
        super.onDestroyView();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        try {
            if (countHandler != null) {
                countHandler.removeCallbacks(countRunnable);
                countHandler.removeCallbacksAndMessages(null);
                countHandler.removeMessages(0);
            }
        } catch (Exception e) {
            logger.error("ConnectorCheckFragment onDetach error : {}", e.getMessage());
        }
    }
}