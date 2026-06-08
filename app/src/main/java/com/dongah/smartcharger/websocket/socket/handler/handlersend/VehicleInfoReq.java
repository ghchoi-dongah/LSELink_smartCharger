package com.dongah.smartcharger.websocket.socket.handler.handlersend;

import android.os.Build;

import androidx.annotation.RequiresApi;

import com.dongah.smartcharger.MainActivity;
import com.dongah.smartcharger.basefunction.ChargerConfiguration;
import com.dongah.smartcharger.basefunction.ChargingCurrentData;
import com.dongah.smartcharger.controlboard.RxData;
import com.dongah.smartcharger.utils.BitUtilities;
import com.dongah.smartcharger.websocket.ocpp.core.datatransfer.lselink.VehicleInfoData;
import com.dongah.smartcharger.websocket.ocpp.core.datatransfer.lselink.VehicleInfoRequest;
import com.google.gson.Gson;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VehicleInfoReq {
    private static final Logger logger = LoggerFactory.getLogger(VehicleInfoReq.class);

    private final int connectorId ;
    public int getConnectorId() {
        return connectorId;
    }

    public VehicleInfoReq(int connectorId) {
        this.connectorId = connectorId;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void sendVehicleInfo() {
        try {
            MainActivity activity = (MainActivity) MainActivity.mContext;

            ChargerConfiguration chargerConfiguration = activity.getChargerConfiguration();
            ChargingCurrentData chargingCurrentData = activity.getChargingCurrentData(getConnectorId()-1);
            RxData rxData = activity.getControlBoard().getRxData(getConnectorId()-1);
            String evccId = BitUtilities.toHexString(rxData.getCsmVehicleEvccId());
//            String evccId = "1364747EE704";   // test code

            VehicleInfoData vehicleInfoData = createVehicleInfoData(evccId);

            VehicleInfoRequest vehicleInfoRequest = new VehicleInfoRequest();
            vehicleInfoRequest.setVendorId(chargerConfiguration.getChargePointVendor());
            vehicleInfoRequest.setMessageId("vehicleInfo");
            Gson gson = new Gson();
            vehicleInfoRequest.setData(gson.toJson(vehicleInfoData));

            activity.getSocketReceiveMessage().onSend(
                    getConnectorId(),
                    vehicleInfoRequest.getActionName(),
                    vehicleInfoRequest);

        } catch (Exception e) {
            logger.error("sendVehicleInfo error : {}", e.getMessage());
        }
    }

    private VehicleInfoData createVehicleInfoData(String evccId) {
        MainActivity activity = (MainActivity) MainActivity.mContext;
        ChargingCurrentData chargingCurrentData = activity.getChargingCurrentData(getConnectorId()-1);

        VehicleInfoData vehicleInfoData = new VehicleInfoData();
        vehicleInfoData.setConnectorId(getConnectorId());
        vehicleInfoData.setIdTag(chargingCurrentData.getIdTag());
        vehicleInfoData.setEvccId(evccId);

        return vehicleInfoData;
    }
}
