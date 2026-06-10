package com.dongah.smartcharger.basefunction;

import android.os.Build;

import androidx.annotation.RequiresApi;

import com.dongah.smartcharger.MainActivity;
import com.dongah.smartcharger.controlboard.RxData;
import com.dongah.smartcharger.websocket.ocpp.core.ChargePointErrorCode;
import com.dongah.smartcharger.websocket.ocpp.core.ChargePointStatus;
import com.dongah.smartcharger.websocket.socket.SocketReceiveMessage;
import com.dongah.smartcharger.websocket.socket.handler.handlersend.ProcessHandler;
import com.dongah.smartcharger.websocket.socket.handler.handlersend.StatusNotificationReq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class NotifyFaultCheck {

    private static final Logger logger = LoggerFactory.getLogger(NotifyFaultCheck.class);

    ProcessHandler processHandler;
    SocketReceiveMessage socketReceiveMessage;
    ChargingCurrentData chargingCurrentData;

    NotifyPropertyChange UiDSP = new NotifyPropertyChange("1021");
    NotifyPropertyChange emergency = new NotifyPropertyChange("1000");
    NotifyPropertyChange csOVR = new NotifyPropertyChange("1005");
    NotifyPropertyChange csOCR = new NotifyPropertyChange("1006");
    NotifyPropertyChange csUnPlug = new NotifyPropertyChange("1009");
    NotifyPropertyChange csUVR = new NotifyPropertyChange("1010");


    //Status
    StatusNotificationReq statusNotificationReq;

    public NotifyFaultCheck() {
        processHandler = ((MainActivity) MainActivity.mContext).getProcessHandler();
        socketReceiveMessage = ((MainActivity) MainActivity.mContext).getSocketReceiveMessage();
        chargingCurrentData = ((MainActivity) MainActivity.mContext).getChargingCurrentData();
        statusNotificationReq =  new StatusNotificationReq(chargingCurrentData.getConnectorId());
    }


    public void onErrorMessageMake(RxData rxData) {
        try {
            chargingCurrentData.faultMessage = new StringBuilder();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                onFaultDetect(rxData);
            }
            if (rxData.csFault || ((MainActivity) MainActivity.mContext).getControlBoard().isDisconnected()) {
                if (((MainActivity) MainActivity.mContext).getControlBoard().isDisconnected())
                    chargingCurrentData.faultMessage.append("UI-Control Board 통신오류\n");
                if (rxData.isCsEmergency()) chargingCurrentData.faultMessage.append("비상 정지\n");
                if (rxData.isCsOVR()) chargingCurrentData.faultMessage.append("OVR 에러\n");
                if (rxData.isCsOCR()) chargingCurrentData.faultMessage.append("OCR 에러\n");
                if (rxData.isCsUVR()) chargingCurrentData.faultMessage.append("UVR 에러\n");
            }
        } catch (Exception e) {
            logger.error("onErrorMessageMake error : {} ", e.getMessage());
        }
    }


    @RequiresApi(api = Build.VERSION_CODES.O)
    private void onFaultDetect(RxData rxData) {
        try {
            boolean disconnected = ((MainActivity) MainActivity.mContext).getControlBoard().isDisconnected();    //true ==> fault 발생

            if (!Objects.equals(UiDSP.ResultCompare, disconnected)) {
                UiDSP.setResultCompare(disconnected);
                if (disconnected) {
                    //발생
                    chargingCurrentData.setChargePointErrorCode(ChargePointErrorCode.EVCommunicationError);
                    chargingCurrentData.setChargePointStatus(ChargePointStatus.Faulted);
                    statusNotificationReq.sendStatusNotification(chargingCurrentData.getConnectorId(), ChargePointStatus.Faulted);

                } else {
                    chargingCurrentData.setChargePointErrorCode(ChargePointErrorCode.NoError);
                    chargingCurrentData.setChargePointStatus(ChargePointStatus.Available);
                    statusNotificationReq.sendStatusNotification(chargingCurrentData.getConnectorId(), ChargePointStatus.Available);
                }
            }

            //unPlug check ( rxData.csPilot == true : plug)
            if (!Objects.equals(csUnPlug.ResultCompare, rxData.csPilot)) {
                csUnPlug.setResultCompare(rxData.csPilot);
                if (!rxData.csPilot) {
                    //unPlug
                    chargingCurrentData.setAutoStart(true);

                    boolean isPlugStatus = Objects.equals(chargingCurrentData.getChargePointStatus(), ChargePointStatus.Finishing) ||
                            Objects.equals(chargingCurrentData.getChargePointStatus(), ChargePointStatus.Preparing);


                    if (isPlugStatus) {
                        chargingCurrentData.setChargePointErrorCode(ChargePointErrorCode.NoError);
                        if (!chargingCurrentData.isConnectUse() || !GlobalVariables.ChargerOperation[1]) {
                            chargingCurrentData.setChargePointStatus(ChargePointStatus.Unavailable);
                        } else {
                            chargingCurrentData.setChargePointStatus(ChargePointStatus.Available);
                        }
                        statusNotificationReq.sendStatusNotification();
                    }

                } else {
                    if (Objects.equals(chargingCurrentData.getChargePointStatus(), ChargePointStatus.Available)) {
                        chargingCurrentData.setChargePointErrorCode(ChargePointErrorCode.NoError);
                        if (!chargingCurrentData.isConnectUse()) {
                            chargingCurrentData.setChargePointStatus(ChargePointStatus.Unavailable);
                            statusNotificationReq.sendStatusNotification(chargingCurrentData.getConnectorId(), ChargePointStatus.Unavailable);
                        } else {
                            chargingCurrentData.setChargePointStatus(ChargePointStatus.Preparing);
                            statusNotificationReq.sendStatusNotification(chargingCurrentData.getConnectorId(), ChargePointStatus.Preparing);
                        }
                    }
                }
            }

            // csEmergency
            if (!Objects.equals(emergency.ResultCompare, rxData.csEmergency)) {
                emergency.setResultCompare(rxData.csEmergency);
                if (rxData.csEmergency) {
                    //발생
                    chargingCurrentData.setChargePointErrorCode(ChargePointErrorCode.OtherError);
                    chargingCurrentData.setChargePointStatus(ChargePointStatus.Faulted);
                    statusNotificationReq.sendStatusNotification();
                } else {
                    chargingCurrentData.setChargePointErrorCode(ChargePointErrorCode.NoError);
                    chargingCurrentData.setChargePointStatus(ChargePointStatus.Available);
                }
            }
        } catch (Exception e) {
            logger.error("onFaultDetect error : {} ", e.getMessage());
        }
    }
}
