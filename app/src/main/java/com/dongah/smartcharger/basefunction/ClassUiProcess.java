package com.dongah.smartcharger.basefunction;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;

import com.dongah.smartcharger.MainActivity;
import com.dongah.smartcharger.R;
import com.dongah.smartcharger.controlboard.ControlBoard;
import com.dongah.smartcharger.controlboard.RxData;
import com.dongah.smartcharger.controlboard.TxData;
import com.dongah.smartcharger.pages.FaultFragment;
import com.dongah.smartcharger.rfcard.RfCardReaderListener;
import com.dongah.smartcharger.rfcard.RfCardReaderReceive;
import com.dongah.smartcharger.websocket.ocpp.core.ChargePointErrorCode;
import com.dongah.smartcharger.websocket.ocpp.core.ChargePointStatus;
import com.dongah.smartcharger.websocket.ocpp.core.Reason;
import com.dongah.smartcharger.websocket.ocpp.core.ResetType;
import com.dongah.smartcharger.websocket.ocpp.utilities.ZonedDateTimeConvert;
import com.dongah.smartcharger.websocket.socket.SocketReceiveMessage;
import com.dongah.smartcharger.websocket.socket.SocketState;
import com.dongah.smartcharger.websocket.socket.handler.handlersend.ChargingAlarmReq;
import com.dongah.smartcharger.websocket.socket.handler.handlersend.MeterValuesReq;
import com.dongah.smartcharger.websocket.socket.handler.handlersend.ProcessHandler;
import com.dongah.smartcharger.websocket.socket.handler.handlersend.StartTransactionReq;
import com.dongah.smartcharger.websocket.socket.handler.handlersend.StatusNotificationReq;
import com.dongah.smartcharger.websocket.socket.handler.handlersend.StopTransactionReq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Objects;

public class ClassUiProcess implements RfCardReaderListener {

    private static final Logger logger = LoggerFactory.getLogger(ClassUiProcess.class);

    int ch;
    UiSeq uiSeq;
    UiSeq oSeq;
    ChargerConfiguration chargerConfiguration;
    ChargingCurrentData chargingCurrentData;

    FragmentChange fragmentChange;
    ControlBoard controlBoard;
    NotifyFaultCheck notifyFaultCheck;
    RfCardReaderReceive rfCardReaderReceive;
    SocketReceiveMessage socketReceiveMessage;
    ProcessHandler processHandler;
    ZonedDateTimeConvert zonedDateTimeConvert;
    private Handler eventHandler;
    private Runnable eventRunnable;

    double powerUnitPrice = 0f;
    int powerMeterCheck = 0;
    boolean chargingAlarm = true;
    boolean startCheck = true;
    boolean finishWaitScheduled = false;

    /** OCPP     */
    StatusNotificationReq statusNotificationReq;
    MeterValuesReq meterValuesReq;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public int getCh() {
        return ch;
    }

    public void setCh(int ch) {
        this.ch = ch;
    }

    public UiSeq getUiSeq() {
        return uiSeq;
    }

    public void setUiSeq(UiSeq uiSeq) {
        this.uiSeq = uiSeq;
    }

    public UiSeq getoSeq() {
        return oSeq;
    }

    public void setoSeq(UiSeq oSeq) {
        this.oSeq = oSeq;
    }

    public int getPowerMeterCheck() {
        return powerMeterCheck;
    }

    public void setPowerMeterCheck(int powerMeterCheck) {
        this.powerMeterCheck = powerMeterCheck;
    }


    public ClassUiProcess() {
        try {
            setUiSeq(UiSeq.INIT);
            zonedDateTimeConvert = new ZonedDateTimeConvert();

            // rf card
            rfCardReaderReceive = ((MainActivity) MainActivity.mContext).getRfCardReaderReceive();
            rfCardReaderReceive.setRfCardReaderListener(this);
            // configuration
            chargerConfiguration = ((MainActivity) MainActivity.mContext).getChargerConfiguration();
            // fragment change
            fragmentChange = ((MainActivity) MainActivity.mContext).getFragmentChange();
            // control board
            controlBoard = ((MainActivity) MainActivity.mContext).getControlBoard();
            // alarm check
            notifyFaultCheck = new NotifyFaultCheck();
            // process handler
            processHandler = ((MainActivity) MainActivity.mContext).getProcessHandler();

            statusNotificationReq = new StatusNotificationReq(1);

            // loop
            startEventLoop();
        } catch (Exception e) {
            logger.error("ClassUiProcess - construct error : {}", e.getMessage());
        }
    }

    int getId = 0;
    int channel;
    boolean check;

    /**
     * charging sequence loop
     * server data send : 서버와 연결이 안된 경우 ProcessHandler dump data save
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private void onEventAction() {
        try {
            RxData rxData = controlBoard.getRxData();
            TxData txData = controlBoard.getTxData();
            check = rxData.isCsFault();
            chargingCurrentData = ((MainActivity) MainActivity.mContext).getChargingCurrentData();
            chargingCurrentData.setIntegratedPower(rxData.getActiveEnergy());
            if (getUiSeq().getValue() < 18) onFaultCheck(rxData);

            // sequence check
            switch (getUiSeq()) {
                case NONE:
                case INIT:
                    handleInit(txData);
                    break;

                case REBOOTING:
                    handleRebooting();
                    break;

                case MEMBER_CARD:
                case MEMBER_CHECK_WAIT:
                    break;

                case CONNECTION_FAILED:
                case MEMBER_CHECK_FAILED:
                    if (!rxData.isCsPilot()) {
                        onHome();
                    }
                    break;

                case PLUG_CHECK:
                    handlePlugCheck(rxData, txData);
                    break;

                case CONNECT_CHECK:
                    handleConnectCheck(rxData, txData);
                    break;

                case CHARGING:
                    handleCharging(rxData, txData);
                    break;

                case FINISH_WAIT:
                    handleFinishWait(rxData, txData);
                    break;

                case FINISH:
                    onFinish(rxData);
                    break;

                case FAULT:
                    handleFault(rxData, txData);
                    break;

                case OP_STOP:
                    handleOpStop();
                    break;

                default:
                    break;
            }
        } catch (Exception e) {
            logger.error("ClassUiProcess onEventAction error : {}", e.getMessage());
        }
    }

    public void onHome() {
        try {
            setUiSeq(UiSeq.INIT);
            fragmentChange.onFragmentChange(UiSeq.INIT, "INIT", null);
            rfCardReaderReceive.rfCardReadRelease();
        } catch (Exception e) {
            logger.error("ClassUiProcess onHome error : {}", e.getMessage());
        }
    }

    /** 충전 완료 */
    private void onFinish(RxData rxData) {
        try {
            if (chargingCurrentData.isReBoot()) {
                setUiSeq(UiSeq.INIT);
            }

            if (!rxData.isCsPilot()) {
                onHome();
            }
        } catch (Exception e) {
            logger.error("ClassUiProcess onFinish error : {}", e.getMessage());
        }
    }

    /**
     * 현재 Fragment 찾기
     *
     * @return fragment
     * */
    private Fragment getCurrentFragment() {
        return ((MainActivity) MainActivity.mContext).getSupportFragmentManager().findFragmentById(R.id.frameBody);
    }

    /**
     * Remote Transaction stop
     * */
    public void onRemoteTransactionStop(Reason reason) {
        try {
            UiSeq uiSeq = ((MainActivity) MainActivity.mContext).getClassUiProcess().getUiSeq();
            if (Objects.equals(uiSeq, UiSeq.CHARGING)) {
                controlBoard = ((MainActivity) MainActivity.mContext).getControlBoard();
                chargingCurrentData = ((MainActivity) MainActivity.mContext).getChargingCurrentData();

                controlBoard.getTxData().setMainMC(false);
                controlBoard.getTxData().setPwmDuty((short) 100);
                chargingCurrentData.setUserStop(false);
                chargingCurrentData.setStopReason(reason);
            }
        } catch (Exception e) {
            logger.error("remote stop error : {}", e.getMessage());
        }
    }

    public void onResetStop(ResetType resetType) {
        try {
            UiSeq uiSeq = ((MainActivity) MainActivity.mContext).getClassUiProcess().getUiSeq();
            if (Objects.equals(uiSeq, UiSeq.CHARGING)) {
                controlBoard.getTxData().setMainMC(false);
                controlBoard.getTxData().setPwmDuty((short) 100);
                chargingCurrentData.setUserStop(false);
                chargingCurrentData.setStopReason(resetType == ResetType.Hard ? Reason.HardReset : Reason.SoftReset);
                setUiSeq(UiSeq.FINISH_WAIT);
            }
        } catch (Exception e) {
            logger.error("reset stop error : {} ", e.getMessage());
        }
    }

    private boolean onRebootCheck() {
        boolean result = false;
        try {
            UiSeq uiSeq = ((MainActivity) MainActivity.mContext).getClassUiProcess().getUiSeq();
            result = Objects.equals(UiSeq.REBOOTING, uiSeq) || Objects.equals(UiSeq.INIT, uiSeq)
                        || Objects.equals(UiSeq.OP_STOP, uiSeq);
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
        return result;
    }

    /**
     * Meter value
     *
     * @param connectorId connector id
     */
    public void onMeterValueStart(int connectorId) {
        onMeterValueStop();
        meterValuesReq = new MeterValuesReq(connectorId);
        meterValuesReq.startMeterValues();
    }

    public void onMeterValueStop() {
        if (meterValuesReq != null) {
            meterValuesReq.stopMeterValues();
            meterValuesReq = null;
        }
    }

    private void startMeterValuesWithDelay() {
        if (GlobalVariables.getMeterValueSampleInterval() > 0) {
            handler.postDelayed(() -> {
                onMeterValueStart(getCh()+1);
            }, 10000);
        }
    }

    private void onFaultCheck(RxData rxData) {
        try {
            //충전중 일 때 fault 가 발생한 경우
            if (controlBoard.isDisconnected() || rxData.csFault) {
                if (Objects.equals(getUiSeq(), UiSeq.CHARGING)) {
                    controlBoard.getTxData().setMainMC(false);
                    controlBoard.getTxData().setPwmDuty((short) 100);
                    chargingCurrentData.setChargePointStatus(ChargePointStatus.Faulted);
                    chargingCurrentData.setChargePointErrorCode(ChargePointErrorCode.OtherError);
                    //비회원 충전 요금 단가 조정을 한다.
                    if (Objects.equals(chargingCurrentData.getPaymentType().value(), 2) &&
                            chargingCurrentData.getPrePayment() <= chargingCurrentData.getPowerMeterUsePay()) {
                        chargingCurrentData.setPowerMeterUsePay(chargingCurrentData.getPrePayment());
                    }
                }
                // fault 발생하기 전에 충전 스퀀스 저장
                if (getUiSeq() != UiSeq.FAULT) setoSeq(getUiSeq());
                setUiSeq(UiSeq.FAULT);
            }
            notifyFaultCheck.onErrorMessageMake(rxData);
        } catch (Exception e) {
            logger.error("onFaultCheck error.... : {}", e.toString());
        }
    }

    /**
     * 충전 사용량 계산
     *
     * @param rxData power meter raw data pick
     */
    private void onUsePowerMeter(RxData rxData) {
        try {
            long gapPower = 0;
            double gapPay = 0;
            if (rxData.getActiveEnergy() > 0) {
                //current power meter --> chargingCurrentData .powerKwh
                //전력량 변화 여부 체크
                chargingCurrentData = ((MainActivity) MainActivity.mContext).getChargingCurrentData();
                gapPower = rxData.getActiveEnergy() - chargingCurrentData.getPowerMeterCalculate();
                gapPower = (gapPower <= 0) ? 0 : (gapPower > 300) ? 100 : gapPower;
                //전력량 변화 여부 체크 892 = 8.92kW
                powerMeterCheck = gapPower == 0 ? powerMeterCheck + 1 : 0;
                chargingCurrentData.setPowerMeterUse(chargingCurrentData.getPowerMeterUse() + gapPower);
                gapPay = gapPower * 0.001 * powerUnitPrice;

                chargingCurrentData.setPowerMeterUsePay(chargingCurrentData.getPowerMeterUsePay() + gapPay);
                chargingCurrentData.setPowerMeterCalculate(rxData.getActiveEnergy());
            }
            chargingCurrentData.setOutPutCurrent(rxData.getCurrent());  //출력전류
            chargingCurrentData.setOutPutVoltage(rxData.getVoltage());  //출력전압
            chargingCurrentData.setPowerMeter(rxData.getActiveEnergy());  //전력량
            chargingCurrentData.setFrequency(rxData.getFrequency() * 0.01);    //주파수
//            chargingCurrentData.setChargingRemainTime(rxData.getRemainTime());  // 충전 남은 시간
//            chargingCurrentData.setSoc(rxData.getSoc());
        } catch (Exception e) {
            logger.error("power meter calculate error : {}", e.getMessage());
        }
    }

    /**
     * Rf CARD reader
     * @param cardNum card number
     * @param value boolean
     */
    @Override
    public void onRfCardDataReceive(String cardNum, boolean value) {
        try {
            if (cardNum.isEmpty() || Objects.equals(cardNum,"0000000000000000")) {
                MainActivity activity = (MainActivity) MainActivity.mContext;
                setUiSeq(UiSeq.INIT);
                fragmentChange.onFragmentChange(UiSeq.INIT,"INIT",null);

                activity.runOnUiThread(() -> {
                    Toast.makeText(activity, "카드 리더기에서 응답이 없습니다.", Toast.LENGTH_SHORT).show();
                });
            } else {
                onRfCardDataReceiveEvent(cardNum, true);
            }
        } catch (Exception e) {
            logger.error("onRfCardDataReceive error : {} ", e.getMessage());
        }
    }

    private void onRfCardDataReceiveEvent(String cardNum, boolean b) {
        if (b) {
            try {
                if (Objects.equals(cardNum,"0000000000000000")) {
                    rfCardReaderReceive.rfCardReadRequest(ch);
                } else if (!cardNum.isEmpty()) {
                    MainActivity activity = ((MainActivity) MainActivity.mContext);
                    ChargingCurrentData chargingCurrentData = activity.getChargingCurrentData();

                    chargingCurrentData.setAuthType("C");
                    chargingCurrentData.setIdTag(cardNum);

                    activity.getClassUiProcess().setUiSeq(UiSeq.MEMBER_CHECK_WAIT);
                    fragmentChange.onFragmentChange(UiSeq.MEMBER_CHECK_WAIT,"MEMBER_CHECK_WAIT",null);
                    rfCardReaderReceive.rfCardReadRelease();
                }
            } catch (Exception e) {
                logger.error("onRfCardDataReceiveEvent error : {} ", e.getMessage());
            }
        }
    }

    private void startEventLoop() {
        eventHandler = new Handler(Looper.getMainLooper());
        eventRunnable = new Runnable() {
            @RequiresApi(api = Build.VERSION_CODES.O)
            @Override
            public void run() {
                onEventAction();
                eventHandler.postDelayed(this, 500);
            }
        };
        eventHandler.postDelayed(eventRunnable, 3000);
    }

    public void stopEventLoop() {
        if (eventHandler != null && eventRunnable != null) {
            eventHandler.removeCallbacks(eventRunnable);
        }
    }

    // init
    private void handleInit(TxData txData) {
        setoSeq(UiSeq.INIT);
        setPowerMeterCheck(0);
        txData.setPwmDuty((short) 100);
        txData.setUiSequence((short) 1);
        chargingAlarm = startCheck = true;
        finishWaitScheduled = false;
        onMeterValueStop();
        if (chargingCurrentData.getChargePointStatus() == ChargePointStatus.Reserved) {

        }

        if (chargingCurrentData.isReBoot() && onRebootCheck()) {
            setUiSeq(UiSeq.REBOOTING);
        }
        // 운영 중지
        else if (!GlobalVariables.ChargerOperation[getCh()+1]) {
            setUiSeq(UiSeq.OP_STOP);
            fragmentChange.onFragmentChange(UiSeq.OP_STOP, "OP_STOP", null);
        }
    }

    // Rebooting
    private void handleRebooting() {
        try {
            if (!(getCurrentFragment() instanceof FaultFragment)) {
                fragmentChange.onFragmentChange(
                        UiSeq.REBOOTING,
                        "REBOOTING",
                        chargingCurrentData.getStopReason() == Reason.HardReset ? "Hard" : "Soft"
                );
            }
        } catch (Exception e) {
            logger.error("ClassUiProcess handleRebooting error : {}", e.getMessage());
        }
    }

    // plug check
    private void handlePlugCheck(RxData rxData, TxData txData) {
        if (rxData.isCsPilot()) {
            txData.setPwmDuty((short) chargerConfiguration.getDuty());
            setUiSeq(UiSeq.CONNECT_CHECK);
        }
    }

    // connect check
    @RequiresApi(api = Build.VERSION_CODES.O)
    private void handleConnectCheck(RxData rxData, TxData txData) {
        // MC ON
        if (Objects.equals(rxData.getCsCPStatus(), (short) 1)) {
            txData.setPwmDuty((short) chargerConfiguration.getDuty());
        }
        if (Objects.equals(rxData.getCsCPStatus(), (short) 2) && startCheck) {
            txData.setMainMC(true);
            powerUnitPrice = Objects.equals(chargerConfiguration.getOpMode(), 1) ?
                    chargingCurrentData.getPowerUnitPrice() : Double.parseDouble(chargerConfiguration.getTestPrice());
            chargingCurrentData.setChargePointStatus(ChargePointStatus.Charging);
            chargingCurrentData.setPowerMeterStart(rxData.getActiveEnergy());                       // 전력량 와트(w) 기준
            chargingCurrentData.setPowerMeterCalculate(chargingCurrentData.getPowerMeterStart());   // 전력량 와트(w) 기준
            chargingCurrentData.setChargingStartTime(zonedDateTimeConvert.getStringCurrentTimeZone());

            // Auto 및 Test mode
            // socket receive message get instance
            socketReceiveMessage = ((MainActivity) MainActivity.mContext).getSocketReceiveMessage();
            if (!Objects.equals(chargerConfiguration.getOpMode(), 1) ||
                    (SocketState.OPEN != socketReceiveMessage.getSocket().getState() && !GlobalVariables.isStopTransactionOnInvalidId())) {
                setUiSeq(UiSeq.CHARGING);
                ((MainActivity) MainActivity.mContext).getFragmentChange().onFragmentChange(UiSeq.CHARGING, "CHARGING", null);
            }
            // server mode
            if (Objects.equals(chargerConfiguration.getOpMode(), 1)) {
                // start transaction send to server(Accepted → Charging Fragment)
                StartTransactionReq startTransactionReq = new StartTransactionReq(chargingCurrentData.getConnectorId());
                startTransactionReq.sendStartTransactionReq();
                startMeterValuesWithDelay();
                startCheck = false;
            }
        }
//        else if (rxData.isCsStop() || rxData.getCsmStatusCode() == (byte) 0x10) {
//            controlBoard.getTxData(getCh()).setStop(true);
//            controlBoard.getTxData(getCh()).setStart(false);
//        }
    }

    // charging
    @RequiresApi(api = Build.VERSION_CODES.O)
    private void handleCharging(RxData rxData, TxData txData) {
        try {
            // 충전 사용량 계산
            onUsePowerMeter(rxData);
            txData.setUiSequence((short) 2);

            txData.setPwmDuty((short) chargerConfiguration.getDuty());
            txData.setMainMC(true);

            ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);

//            if (chargingCurrentData.isExtendedRemoteStart()) {
//                checkExtendedStop(now);
//            } else {
//                checkNormalStop(rxData, now);
//            }

            // target soc
            int targetSoc = Math.min(chargingCurrentData.getLimitSoc(), chargingCurrentData.getFullrechgsoc());
            if (targetSoc == 0) {
                targetSoc = chargerConfiguration.getTargetSoc();
            }
            boolean isSocReached = (chargingCurrentData.getSoc() != 0
                    && chargingCurrentData.getSoc() >= targetSoc);

            // 충전율 90%
            if (Objects.equals(chargingCurrentData.getSoc(), 90) && chargingAlarm) {
                ChargingAlarmReq chargingAlarmReq = new ChargingAlarmReq(chargingCurrentData.getConnectorId());
                chargingAlarmReq.sendChargingAlarmReq(2);
                chargingAlarm = false;  // 알림을 한 번만 보내기 위함
            }

            // stop 조건
            if (!GlobalVariables.isStopTransactionOnEVSideDisconnect() &&
                    !GlobalVariables.isUnlockConnectorOnEVSideDisconnect()) {
                if (rxData.isCsStop() || !rxData.isCsPilot() || isSocReached) {
                    if (chargingCurrentData.getStopReason() == Reason.Remote || chargingCurrentData.isUserStop()) {
//                        controlBoard.getTxData().setStop(true);
//                        controlBoard.getTxData().setStart(false);
                        if (!rxData.isCsPilot()) {
                            // status notification send to server : ChargePointStatus.SuspendedEV
                            // 2.4.5. EV Side Disconnected
                            chargingCurrentData.setStopReason(Reason.EVDisconnected);
                        }
                        setUiSeq(UiSeq.FINISH_WAIT);
                        fragmentChange.onFragmentChange(UiSeq.FINISH_WAIT, "FINISH_WAIT", null);
                    }
                }
            } else {
                if (rxData.isCsStop() || !rxData.isCsPilot() || chargingCurrentData.isUserStop() || isSocReached
                        || !GlobalVariables.ChargerOperation[getCh()+1]) {
//                    controlBoard.getTxData().setStop(true);
//                    controlBoard.getTxData().setStart(false);
                    if (!rxData.isCsPilot()) {
                        // status notification send to server : ChargePointStatus.SuspendedEV
                        // 2.4.5. EV Side Disconnected
                        chargingCurrentData.setStopReason(Reason.EVDisconnected);
                    }
                    setUiSeq(UiSeq.FINISH_WAIT);
                    fragmentChange.onFragmentChange(UiSeq.FINISH_WAIT, "FINISH_WAIT", null);
                }
            }
        } catch (Exception e) {
            logger.error("ClassUiProcess - CHARGING error : {}", e.getMessage());
        }
    }

    // finish wait
    @RequiresApi(api = Build.VERSION_CODES.O)
    private void handleFinishWait(RxData rxData, TxData txData) {
        if (finishWaitScheduled) return;   // 이미 처리 중이면 즉시 리턴
        finishWaitScheduled = true;        // 첫 진입 시 잠금

        try {
            txData.setUiSequence((short) 3);
            chargingCurrentData.setStopReason(
                    chargingCurrentData.isUserStop() ? Reason.Local :
                            !rxData.isCsPilot() ? Reason.EVDisconnected :
                                    chargingCurrentData.getStopReason()
            );
            chargingCurrentData.setPowerMeterStop(rxData.getActiveEnergy());
            chargingCurrentData.setChargingEndTime(zonedDateTimeConvert.getStringCurrentTimeZone());
            chargingCurrentData.setChargePointStatus(ChargePointStatus.Finishing);

            // stop MeterValues
            if (meterValuesReq != null) {
                meterValuesReq.sendMeterValues(chargingCurrentData.getConnectorId());
            }
            onMeterValueStop();


            handler.postDelayed(() -> {
                finishWaitScheduled = false;   // 완료 후 해제

                if (Objects.equals(chargerConfiguration.getOpMode(), 1)) {
                    // StopTransaction
                    StopTransactionReq stopTransactionReq = new StopTransactionReq(chargingCurrentData.getConnectorId());
                    stopTransactionReq.sendStopTransactionReq();
                }

                if (!GlobalVariables.ChargerOperation[1]) {
                    setUiSeq(UiSeq.INIT);
                    fragmentChange.onFragmentChange(UiSeq.INIT, "INIT", null);
                } else {
                    setUiSeq(UiSeq.FINISH);
                    fragmentChange.onFragmentChange(UiSeq.FINISH, "FINISH", null);
                }

            }, 3000);
        } catch (Exception e) {
            finishWaitScheduled = false;
            logger.error("ClassUiProcess - FINISH_WAIT error : {} ", e.getMessage());
        }
    }

    // fault
    @RequiresApi(api = Build.VERSION_CODES.O)
    private void handleFault(RxData rxData, TxData txData) throws Exception {
        UiSeq currentViewSeq = ((MainActivity) MainActivity.mContext).getFragmentSeq();
        if (currentViewSeq.getValue() < 18) {
            if (!(getCurrentFragment() instanceof FaultFragment)) {
                // server mode 및 charging
                if (Objects.equals(chargerConfiguration.getOpMode(), 1) &&
                        Objects.equals(getoSeq(), UiSeq.CHARGING)) {
                    chargingCurrentData.setStopReason(rxData.isCsEmergency() ? Reason.EmergencyStop : Reason.Other);
                    txData.setMainMC(false);
                    txData.setPwmDuty((short) 100);
                    chargingCurrentData.setUserStop(false);
                    chargingCurrentData.setPowerMeterStop(rxData.getActiveEnergy());
                    chargingCurrentData.setChargingEndTime(zonedDateTimeConvert.getStringCurrentTimeZone());
                    chargingCurrentData.setChargePointStatus(ChargePointStatus.Finishing);

                    // meter values stop
                    meterValuesReq.sendMeterValues(chargingCurrentData.getConnectorId());
                    onMeterValueStop();

                    handler.postDelayed(() -> {
                        // socket receive message get instance
                        socketReceiveMessage = ((MainActivity) MainActivity.mContext).getSocketReceiveMessage();
                        SocketState state = socketReceiveMessage.getSocket().getState();
                        if (Objects.equals(state.getValue(), 7) && Objects.equals(chargerConfiguration.getOpMode(), 1)) {
                            // server send
                            StopTransactionReq stopTransactionReq = new StopTransactionReq(chargingCurrentData.getConnectorId());
                            stopTransactionReq.sendStopTransactionReq();
                        }
                    }, 3000);
                }
                fragmentChange.onFragmentChange(UiSeq.FAULT, "FAULT", null);
            }
        }

        // fault 해제
        if (!controlBoard.isDisconnected() && !rxData.isCsFault()) {
            if (Objects.equals(getoSeq(), UiSeq.CHARGING)) {
                chargingCurrentData.setChargePointStatus(ChargePointStatus.Finishing);
                chargingCurrentData.setChargePointErrorCode(ChargePointErrorCode.NoError);
                setUiSeq(UiSeq.FINISH);
                fragmentChange.onFragmentChange( UiSeq.FINISH, "FINISH", null);
            } else {
                if (Objects.equals(chargingCurrentData.getChargePointStatus(), ChargePointStatus.Preparing) &&
                        !rxData.isCsPilot()) {
                    chargingCurrentData.setChargePointStatus(ChargePointStatus.Available);
                    // socket receive message get instance
                    socketReceiveMessage = ((MainActivity) MainActivity.mContext).getSocketReceiveMessage();
                    SocketState state = socketReceiveMessage.getSocket().getState();
                    if (Objects.equals(state.getValue(), 7) && Objects.equals(chargerConfiguration.getOpMode(), 1)) {
                        statusNotificationReq.sendStatusNotification();
                    }
                }
                onHome();
            }
        }
    }

    // 점검 상태
    private void handleOpStop() {
        // OP_STOp → INIT
        if (GlobalVariables.ChargerOperation[getCh()+1]) {
            setUiSeq(UiSeq.INIT);
            fragmentChange.onFragmentChange(UiSeq.INIT, "INIT", null);
        }

        if (chargingCurrentData.isReBoot() && onRebootCheck()) {
            setUiSeq(UiSeq.REBOOTING);
        }
    }
}
