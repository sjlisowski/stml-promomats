package com.veeva.vault.custom.actions.document;

import com.veeva.vault.custom.udc.CompliancePackage;
import com.veeva.vault.sdk.api.action.DocumentAction;
import com.veeva.vault.sdk.api.action.DocumentActionContext;
import com.veeva.vault.sdk.api.action.DocumentActionInfo;
import com.veeva.vault.sdk.api.action.Usage;
import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.document.DocumentVersion;
import com.veeva.vault.sdk.api.job.JobParameters;
import com.veeva.vault.sdk.api.job.JobRunResult;
import com.veeva.vault.sdk.api.job.JobService;

/**
 * This class
 *    - updates the Submission Date field on bound Material documents to match the Submission Date field
 *      in the Compliance Package binder.
 *    - invokes a Job to move all of the Material documents to the "Approved for Distribution" status
 *      (or "Awaiting Health Authority Response" if applicable)
 */

@DocumentActionInfo(
	label="Compliance Package Completion",
	lifecycle="compliance_package__c",
	usages={Usage.LIFECYCLE_ENTRY_ACTION, Usage.USER_ACTION}
)
public class CompliancePackageCompletion implements DocumentAction {
	
    public void execute(DocumentActionContext documentActionContext) {

    	DocumentVersion binderDocVersion = documentActionContext.getDocumentVersions().get(0);
    	String binderId = binderDocVersion.getValue("id", ValueType.STRING);
			CompliancePackage.SetMaterialSubmissionDates(binderId);

			JobService jobService = ServiceLocator.locate(JobService.class);
			JobParameters jobParameters = jobService.newJobParameters("compliance_package_completion__c");
			jobParameters.setValue("binderId", binderId);
			JobRunResult result = jobService.runJob(jobParameters);

    }

	public boolean isExecutable(DocumentActionContext documentActionContext) {
	    return true;
	}
}