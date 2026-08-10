package com.dongah.smartcharger.websocket.socket.handler.handlerreceive;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.dongah.smartcharger.MainActivity;
import com.dongah.smartcharger.basefunction.GlobalVariables;
import com.dongah.smartcharger.sqlite.SQLiteHelper;
import com.dongah.smartcharger.sqlite.dto.BatteryEncryptKey;
import com.dongah.smartcharger.sqlite.dto.CpChangeMode;
import com.dongah.smartcharger.websocket.ocpp.core.DataTransferStatus;
import com.dongah.smartcharger.websocket.ocpp.core.datatransfer.lselink.BatteryEncryptKeyConfirm;
import com.dongah.smartcharger.websocket.ocpp.utilities.ZonedDateTimeConvert;
import com.dongah.smartcharger.websocket.socket.OcppHandler;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatteryEncryptKeyHandler implements OcppHandler {
    private static final Logger logger = LoggerFactory.getLogger(BatteryEncryptKeyHandler.class);

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void handle(JSONObject payload, int connectorId, String messageId) throws Exception {
        MainActivity activity = (MainActivity) MainActivity.mContext;
        DataTransferStatus status = DataTransferStatus.Accepted;

        try {
            JSONObject dataJson = payload.getJSONObject("data");
            String dataStr = payload.getString("data");
            String chargerId = dataJson.getString("chargerId");
            String keyId = dataJson.getString("keyId");
            GlobalVariables.setBatteryEncryptKeyId(keyId);
            logger.info("BatteryEncryptKey received keyId: {}", keyId);

            updateBatteryEncryptKey(dataStr, chargerId);
        } catch (Exception e) {
            status = DataTransferStatus.Rejected;
            logger.error("BatteryEncryptKeyHandler error : {}", e.getMessage(), e);
        }

        // response
        BatteryEncryptKeyConfirm batteryEncryptKeyConfirm = new BatteryEncryptKeyConfirm(status);
        activity.getSocketReceiveMessage().onResultSend(
                connectorId,
                batteryEncryptKeyConfirm.getActionName(),
                messageId,
                batteryEncryptKeyConfirm
        );
    }

    // update
    @RequiresApi(api = Build.VERSION_CODES.O)
    private void updateBatteryEncryptKey(String dataStr, String chargerId) {
        try {
            MainActivity activity = (MainActivity) MainActivity.mContext;
            SQLiteHelper helper = SQLiteHelper.getInstance(activity);
            SQLiteDatabase sqLiteDatabase = helper.getWritableDatabase();
            String tableName = new CpChangeMode().getTableName();

            JSONObject dataJson = new JSONObject(dataStr);

            // 테이블이 없으면 테이블 생성 후 업데이트 진행
            if (!helper.isTableExists(helper, tableName)) {
                logger.warn("updateBatteryEncryptKey table not exists : {}", tableName);
                helper.onCreateTable(sqLiteDatabase, tableName);
                insertBatteryEncryptKey(helper, dataJson);
                return;
            }

            // chargerId 존재 유무 확인
            Cursor cursor = helper.select(tableName, "CHARGER_ID", new String[]{String.valueOf(chargerId)});
            // Cursor null 여부 확인, 조회 결과 존재 여부 확인
            if (cursor == null || !cursor.moveToFirst()) {
                logger.warn("updateBatteryEncryptKey {} cursor is null or no data. chargerId : {}", tableName, chargerId);
                insertBatteryEncryptKey(helper, dataJson);
                return;
            }

            ContentValues values = new ContentValues();
            values.put("KEY_ID", dataJson.getString("keyId"));
            values.put("ENCRYPT_PUB", dataJson.getString("encryptPub"));
            values.put("SIGN_DATA", dataJson.getString("signData"));
            values.put("VALID_TIME", dataJson.getString("validTime"));
            values.put("RET_VAL", dataJson.getString("retVal"));

            // REG_DT
            ZonedDateTimeConvert convert = new ZonedDateTimeConvert();
            values.put("REG_DT", convert.doGetKstDatetimeAsString());

            // DB 업데이트
            int updated = helper.update(tableName, values, "CHARGER_ID", new String[]{String.valueOf(chargerId)});
            logger.info("updateChgMode chargerId: {} updated rows : {}", chargerId, updated);

            cursor.close();
        } catch (Exception e) {
            logger.error("updateBatteryEncryptKey error : {}", e.getMessage(), e);
        }
    }

    // insert
    @RequiresApi(api = Build.VERSION_CODES.O)
    private void insertBatteryEncryptKey(SQLiteHelper sqLiteHelper, JSONObject dataJson) {
        try {
            BatteryEncryptKey batteryEncryptKey = new BatteryEncryptKey();
            batteryEncryptKey.chargerId = dataJson.getString("chargerId");
            batteryEncryptKey.keyId = dataJson.getString("keyId");
            batteryEncryptKey.encryptPub = dataJson.getString("encryptPub");
            batteryEncryptKey.signData = dataJson.getString("signData");
            batteryEncryptKey.validTime = dataJson.getString("validTime");
            batteryEncryptKey.retVal = dataJson.getString("retVal");

            ZonedDateTimeConvert zonedDateTimeConvert = new ZonedDateTimeConvert();
            batteryEncryptKey.regDt = zonedDateTimeConvert.doGetKstDatetimeAsString();

            sqLiteHelper.insert(batteryEncryptKey);
        } catch (Exception e) {
            logger.error("insertBatteryEncryptKey error : {}", e.getMessage(), e);
        }
    }
}
