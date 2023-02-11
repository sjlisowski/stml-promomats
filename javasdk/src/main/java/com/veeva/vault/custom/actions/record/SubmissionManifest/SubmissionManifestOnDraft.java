package com.veeva.vault.custom.actions.record.SubmissionManifest;

import com.veeva.vault.custom.udc.SubmissionManifest;
import com.veeva.vault.sdk.api.action.RecordAction;
import com.veeva.vault.sdk.api.action.RecordActionContext;
import com.veeva.vault.sdk.api.action.RecordActionInfo;
import com.veeva.vault.sdk.api.action.Usage;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.data.Record;

/**
 * This record action executes the business logic required when a Submission Manifest (submission_manifest__c)
 * object record is returned to the Draft status.
 *
 * See UDC method SubmissioinManifest.onSubmit() for details.
 */

@RecordActionInfo(label="On Return Submission Manifest to Draft", object="submission_manifest__c",
  usages={Usage.WORKFLOW_STEP})
public class SubmissionManifestOnDraft implements RecordAction {

    public void execute(RecordActionContext recordActionContext) {

        Record record = recordActionContext.getRecords().get(0);
        String manifestId = record.getValue("id", ValueType.STRING);

        SubmissionManifest.OnReturnToDraft(manifestId);

    }

    public boolean isExecutable(RecordActionContext recordActionContext) {
        return true;
    }
}