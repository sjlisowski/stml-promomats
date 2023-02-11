package com.veeva.vault.custom.actions.record.SubmissionManifest;

import com.veeva.vault.custom.udc.ErrorType;
import com.veeva.vault.custom.udc.Result;
import com.veeva.vault.custom.udc.SubmissionManifest;
import com.veeva.vault.sdk.api.action.RecordAction;
import com.veeva.vault.sdk.api.action.RecordActionContext;
import com.veeva.vault.sdk.api.action.RecordActionInfo;
import com.veeva.vault.sdk.api.action.Usage;
import com.veeva.vault.sdk.api.core.RollbackException;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.data.Record;

/**
 * This record action executes the business logic required when a Submission Manifest (submission_manifest__c)
 * object record is submitted to Regulatory Operations.
 *
 * This action is executed as part of a workflow.  If the action completes successfully, the Submission
 * Manifest object record goes to the "Submission Requested" status.
 *
 * See UDC method SubmissionManifest.onSubmit() for details.
 */

@RecordActionInfo(label="On Submit Submission Manifest", object="submission_manifest__c", 
  usages={Usage.WORKFLOW_STEP})
public class SubmissionManifestOnSubmit implements RecordAction {

    public void execute(RecordActionContext recordActionContext) {

        Record record = recordActionContext.getRecords().get(0);
        String manifestId = record.getValue("id", ValueType.STRING);

        Result result = SubmissionManifest.OnSubmit(manifestId);

        if (result.success == false) {
          throw new RollbackException(ErrorType.OPERATION_DENIED, result.message);
        }
    }

    public boolean isExecutable(RecordActionContext recordActionContext) {
        return true;
    }
}