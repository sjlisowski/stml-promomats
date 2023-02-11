package com.veeva.vault.custom.actions.record.SubmissionManifest;

import com.veeva.vault.custom.udc.VaultAPI;
import com.veeva.vault.sdk.api.action.RecordAction;
import com.veeva.vault.sdk.api.action.RecordActionContext;
import com.veeva.vault.sdk.api.action.RecordActionInfo;
import com.veeva.vault.sdk.api.action.Usage;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.data.Record;

/**
 * This record action tests to see if we can change a Record's lifecycle state.
 */

@RecordActionInfo(label="Test module for Submission Manifest", object="submission_manifest__c",
  usages={Usage.USER_ACTION})
public class SubmissionManifestTest implements RecordAction {

    private static final String SubmissionManifestObjectName = "submission_manifest__c";

    public void execute(RecordActionContext recordActionContext) {

        Record record = recordActionContext.getRecords().get(0);
        String manifestId = record.getValue("id", ValueType.STRING);

        VaultAPI vaultAPI = new VaultAPI("local_connection__c");

        String actionName = vaultAPI.getObjectUserActionName(
          SubmissionManifestObjectName, manifestId, "Submit to Regulatory Operations"
        );

        if (vaultAPI.failed()) {
          String errorType = vaultAPI.getErrorType();
          String errorMsg = vaultAPI.getErrorMessage();
        }

        vaultAPI.initiateObjectRecordUserAction(SubmissionManifestObjectName, manifestId, actionName);

        if (vaultAPI.failed()) {
            String errorType = vaultAPI.getErrorType();
            String errorMsg = vaultAPI.getErrorMessage();
        }

        int stop=0;
    }

    public boolean isExecutable(RecordActionContext recordActionContext) {
        return true;
    }
}