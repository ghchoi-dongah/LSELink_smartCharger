package com.dongah.smartcharger.websocket.socket.handler.handlerreceive;

import android.os.Build;

import androidx.annotation.RequiresApi;

import com.dongah.smartcharger.MainActivity;
import com.dongah.smartcharger.basefunction.ChargerConfiguration;
import com.dongah.smartcharger.basefunction.GlobalVariables;
import com.dongah.smartcharger.websocket.ocpp.core.BootNotificationRequest;
import com.dongah.smartcharger.websocket.ocpp.core.HeartbeatRequest;
import com.dongah.smartcharger.websocket.ocpp.firmware.DiagnosticsStatusNotificationRequest;
import com.dongah.smartcharger.websocket.ocpp.firmware.FirmwareStatusNotificationRequest;
import com.dongah.smartcharger.websocket.ocpp.remotetrigger.TriggerMessageConfirmation;
import com.dongah.smartcharger.websocket.ocpp.remotetrigger.TriggerMessageRequestType;
import com.dongah.smartcharger.websocket.ocpp.remotetrigger.TriggerMessageStatus;
import com.dongah.smartcharger.websocket.socket.OcppHandler;
import com.dongah.smartcharger.websocket.socket.SocketReceiveMessage;
import com.dongah.smartcharger.websocket.socket.handler.handlersend.MeterValuesReq;
import com.dongah.smartcharger.websocket.socket.handler.handlersend.StatusNotificationReq;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class TriggerMessageHandler implements OcppHandler {
    private static final Logger logger = LoggerFactory.getLogger(TriggerMessageHandler.class);

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void handle(JSONObject payload, int connectorId, String messageId) throws Exception {
        MainActivity activity = (MainActivity) MainActivity.mContext;
        SocketReceiveMessage socketReceiveMessage = activity.getSocketReceiveMessage();
        ChargerConfiguration chargerConfiguration = activity.getChargerConfiguration();
        TriggerMessageRequestType triggerMessageRequestType = TriggerMessageRequestType.valueOf(payload.getString("requestedMessage"));
        TriggerMessageStatus status = TriggerMessageStatus.Accepted;
        TriggerMessageConfirmation triggerMessageConfirmation = new TriggerMessageConfirmation(status);
        activity.getSocketReceiveMessage().onResultSend(
                connectorId,
                triggerMessageConfirmation.getActionName(),
                messageId,
                triggerMessageConfirmation
        );

        if (Objects.equals(triggerMessageRequestType, TriggerMessageRequestType.BootNotification)) {
            BootNotificationRequest bootNotificationRequest = new BootNotificationRequest(
                    chargerConfiguration.getChargePointVendor(),
                    chargerConfiguration.getChargerPointModel()
            );
            bootNotificationRequest.setFirmwareVersion(GlobalVariables.FW_VERSION);
            bootNotificationRequest.setImsi(chargerConfiguration.getImsi());
            bootNotificationRequest.setChargePointSerialNumber(chargerConfiguration.getChargerId());
            bootNotificationRequest.setIccid(chargerConfiguration.getIccid());

            socketReceiveMessage.onSend(
                    100,
                    bootNotificationRequest.getActionName(),
                    bootNotificationRequest
            );
        } else if (Objects.equals(triggerMessageRequestType, TriggerMessageRequestType.DiagnosticsStatusNotification)) {
            DiagnosticsStatusNotificationRequest diagnosticsStatusNotificationRequest =
                    new DiagnosticsStatusNotificationRequest(chargerConfiguration.getDiagnosticsStatus());
            socketReceiveMessage.onSend(
                    100,
                    diagnosticsStatusNotificationRequest.getActionName(),
                    diagnosticsStatusNotificationRequest
            );
        } else if (Objects.equals(triggerMessageRequestType, TriggerMessageRequestType.FirmwareStatusNotification)) {
            FirmwareStatusNotificationRequest firmwareStatusNotificationRequest =
                    new FirmwareStatusNotificationRequest(chargerConfiguration.getFirmwareStatus());
            socketReceiveMessage.onSend(
                    100,
                    firmwareStatusNotificationRequest.getActionName(),
                    firmwareStatusNotificationRequest
            );
        } else if (Objects.equals(triggerMessageRequestType, TriggerMessageRequestType.Heartbeat)) {
            HeartbeatRequest heartbeatRequest = new HeartbeatRequest();
            socketReceiveMessage.onSend(
                    100,
                    heartbeatRequest.getActionName(),
                    heartbeatRequest
            );
        } else if (Objects.equals(triggerMessageRequestType, TriggerMessageRequestType.MeterValues)) {
            GlobalVariables.setTriggerSet(true);
            MeterValuesReq meterValuesReq = new MeterValuesReq(connectorId);
            meterValuesReq.sendMeterValues(connectorId);
        } else if (Objects.equals(triggerMessageRequestType, TriggerMessageRequestType.StatusNotification)) {
            StatusNotificationReq statusNotificationReq = new StatusNotificationReq(connectorId);
            statusNotificationReq.sendStatusNotification();
        }
    }
}
