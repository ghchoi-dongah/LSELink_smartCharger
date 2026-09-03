package com.dongah.smartcharger.websocket.socket.handler.handlerreceive;

import android.os.Build;

import androidx.annotation.RequiresApi;

import com.dongah.smartcharger.MainActivity;
import com.dongah.smartcharger.basefunction.ChargingCurrentData;
import com.dongah.smartcharger.basefunction.FragmentChange;
import com.dongah.smartcharger.basefunction.GlobalVariables;
import com.dongah.smartcharger.basefunction.UiSeq;
import com.dongah.smartcharger.controlboard.TxData;
import com.dongah.smartcharger.websocket.ocpp.core.AuthorizationStatus;
import com.dongah.smartcharger.websocket.ocpp.core.ChargePointStatus;
import com.dongah.smartcharger.websocket.ocpp.utilities.ZonedDateTimeConvert;
import com.dongah.smartcharger.websocket.socket.OcppHandler;
import com.dongah.smartcharger.websocket.socket.handler.handlersend.ChargingAlarmReq;
import com.dongah.smartcharger.websocket.socket.handler.handlersend.FullRechgSocReq;
import com.dongah.smartcharger.websocket.socket.handler.handlersend.StatusNotificationReq;
import com.dongah.smartcharger.websocket.socket.handler.handlersend.StopTransactionReq;
import com.dongah.smartcharger.websocket.socket.handler.handlersend.UserSetSocReq;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class StartTransactionHandler implements OcppHandler  {
    private static final Logger logger = LoggerFactory.getLogger(StartTransactionHandler.class);

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void handle(JSONObject payload, int connectorId, String messageId) throws Exception {
        MainActivity activity = ((MainActivity) MainActivity.mContext);
        ChargingCurrentData chargingCurrentData = activity.getChargingCurrentData();
        //서버에서 transactionId 받음 ==> stopTransaction 계속하여 사용.
        int transactionId = payload.getInt("transactionId");
        chargingCurrentData.setTransactionId(transactionId);

        JSONObject idTagInfo = payload.getJSONObject("idTagInfo");
        AuthorizationStatus status = AuthorizationStatus.valueOf(idTagInfo.getString("status"));
        String parentIdTag = idTagInfo.has("parentIdTag") ? idTagInfo.getString("parentIdTag") : "";

        // dump data
        if (GlobalVariables.isDumpSending(connectorId)) {
            logger.info("Dump StartTransaction Conf 수신 : {}", transactionId);
            GlobalVariables.setDumpTransactionId(connectorId, transactionId);
            activity.getSocketReceiveMessage().getSocket()
                    .getDumpDataSend(connectorId).onReceiveStartTransactionConf(connectorId, transactionId);
            return;
        }

        //accept continue
        if (Objects.equals(status, AuthorizationStatus.Accepted)) {
            chargingCurrentData.setChargePointStatus(ChargePointStatus.Charging);

            if (GlobalVariables.RemoteStart) {
                GlobalVariables.remoteConnectorId.put(connectorId, transactionId);
            }

            // DataTransfer ChargingAlarm
            ChargingAlarmReq chargingAlarmReq = new ChargingAlarmReq(connectorId);
            chargingAlarmReq.sendChargingAlarmReq(1);

            // DataTransfer fullrechgsoc - B2C 사용X
//            FullRechgSocReq fullRechgSocReq = new FullRechgSocReq(connectorId);
//            fullRechgSocReq.sendFullRechSoc();

            // StatusNotification
            StatusNotificationReq statusNotificationReq = new StatusNotificationReq(connectorId);
            statusNotificationReq.sendStatusNotification(connectorId, ChargePointStatus.Charging);

            // DataTransfer userSetSoc
            UserSetSocReq userSetSocReq = new UserSetSocReq(connectorId);
            userSetSocReq.sendUserSetSoc();

            activity.getClassUiProcess().setUiSeq(UiSeq.CHARGING);
            FragmentChange fragmentChange = new FragmentChange();
            fragmentChange.onFragmentChange(UiSeq.CHARGING, "CHARGING", null);
        } else {
            // stop
            TxData txData = activity.getControlBoard().getTxData();
            txData.setUiSequence((short) 3);

            // DataTransfer MeterValues
            activity.getClassUiProcess().onMeterValueStop();

            // StopTransaction
            ZonedDateTimeConvert zonedDateTimeConvert = new ZonedDateTimeConvert();
            chargingCurrentData.setPowerMeterStop(chargingCurrentData.getPowerMeterStart());
            chargingCurrentData.setChargingEndTime(zonedDateTimeConvert.getStringCurrentTimeZone());
            StopTransactionReq stopTransactionReq = new StopTransactionReq(connectorId);
            stopTransactionReq.sendStopTransactionReq();

            // home
            activity.getClassUiProcess().onHome();
        }
    }
}
