package com.dongah.smartcharger.websocket.socket.handler.handlerreceive;

import android.os.Build;

import androidx.annotation.RequiresApi;

import com.dongah.smartcharger.MainActivity;
import com.dongah.smartcharger.basefunction.FirmwareDownload;
import com.dongah.smartcharger.basefunction.GlobalVariables;
import com.dongah.smartcharger.websocket.ocpp.core.DataTransferStatus;
import com.dongah.smartcharger.websocket.ocpp.core.datatransfer.lselink.UpdateCertificationConfirm;
import com.dongah.smartcharger.websocket.socket.OcppHandler;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

public class UpdateCertificationHandler implements OcppHandler {
    private static final Logger logger = LoggerFactory.getLogger(UpdateCertificationHandler.class);
    private static final int CERT_COUNT = 4;
    private static final int DOWNLOAD_RETRY = 3;

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void handle(JSONObject payload, int connectorId, String messageId) throws Exception {
        try {
            JSONObject dataJson = payload.getJSONObject("data");

            String kmePublicUrl    = dataJson.getString("kmePublic").trim();
            String kmeCertiUrl     = dataJson.getString("kmeCerti").trim();
            String chargerPrivUrl  = dataJson.getString("chargerPrivate").trim();
            String chargerCertiUrl = dataJson.getString("chargerCerti").trim();
            String validTime       = dataJson.getString("validTime").trim();

            GlobalVariables.validTime = validTime;

            MainActivity activity = (MainActivity) MainActivity.mContext;
            AtomicInteger doneCount    = new AtomicInteger(0);
            AtomicInteger successCount = new AtomicInteger(0);

            FirmwareDownload.Callback callback = new FirmwareDownload.Callback() {
                @Override
                public void onSuccess(File file) {
                    successCount.incrementAndGet();
                    checkAllDone();
                }

                @Override
                public void onFail(String error) {
                    logger.error("Certificate download failed: {}", error);
                    checkAllDone();
                }

                void checkAllDone() {
                    if (doneCount.incrementAndGet() < CERT_COUNT) return;

                    boolean allSuccess = successCount.get() == CERT_COUNT;
                    if (allSuccess) {
                        GlobalVariables.kmePublic      = GlobalVariables.getRootPath() + File.separator + extractFileName(kmePublicUrl);
                        GlobalVariables.kmeCerti       = GlobalVariables.getRootPath() + File.separator + extractFileName(kmeCertiUrl);
                        GlobalVariables.chargerPrivate = GlobalVariables.getRootPath() + File.separator + extractFileName(chargerPrivUrl);
                        GlobalVariables.chargerCerti   = GlobalVariables.getRootPath() + File.separator + extractFileName(chargerCertiUrl);
                        logger.info("All certificates downloaded. validTime={}", validTime);
                    } else {
                        logger.error("Certificate download incomplete: {}/{} succeeded", successCount.get(), CERT_COUNT);
                    }

                    DataTransferStatus status = allSuccess ? DataTransferStatus.Accepted : DataTransferStatus.Rejected;
                    UpdateCertificationConfirm updateCertificationConfirm = new UpdateCertificationConfirm();
                    updateCertificationConfirm.setStatus(status);
                    try {
                        activity.getSocketReceiveMessage().onResultSend(
                                connectorId,
                                updateCertificationConfirm.getActionName(),
                                messageId,
                                updateCertificationConfirm
                        );
                    } catch (Exception e) {
                        logger.error("UpdateCertification response error: {}", e.getMessage(), e);
                    }
                }
            };

            new FirmwareDownload(kmePublicUrl,    extractFileName(kmePublicUrl),    DOWNLOAD_RETRY, callback).start();
            new FirmwareDownload(kmeCertiUrl,     extractFileName(kmeCertiUrl),     DOWNLOAD_RETRY, callback).start();
            new FirmwareDownload(chargerPrivUrl,  extractFileName(chargerPrivUrl),  DOWNLOAD_RETRY, callback).start();
            new FirmwareDownload(chargerCertiUrl, extractFileName(chargerCertiUrl), DOWNLOAD_RETRY, callback).start();

        } catch (Exception e) {
            logger.error("UpdateCertificationHandler error : {}", e.getMessage(), e);
        }
    }

    private String extractFileName(String url) {
        return url.substring(url.lastIndexOf('/') + 1);
    }
}
