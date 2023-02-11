package com.veeva.vault.custom.actions.document;

import com.veeva.vault.custom.udc.CompliancePackage;
import com.veeva.vault.sdk.api.action.DocumentAction;
import com.veeva.vault.sdk.api.action.DocumentActionContext;
import com.veeva.vault.sdk.api.action.DocumentActionInfo;
import com.veeva.vault.sdk.api.action.Usage;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.document.DocumentVersion;

/**
 * This class compiles data from the Binder's materials into an record of object "Submission Comments".
 * A new object record of type "Submission Comments" is created the first time this module is run
 * for a given Binder.
 */

@DocumentActionInfo(
	label = "Generate Form 2253 Comments",
	lifecycle = "compliance_package__c",
	icon = "update__sys",
	usages = {
		Usage.LIFECYCLE_ENTRY_ACTION,
		Usage.USER_ACTION
	}
)
public class CompliancePackageSubmissionComments implements DocumentAction {
	
    public void execute(DocumentActionContext documentActionContext) {

    	DocumentVersion binderDocVersion = documentActionContext.getDocumentVersions().get(0);
			String binderId = binderDocVersion.getValue("id", ValueType.STRING);

			CompliancePackage.Generate2253Comments(binderId);

    }

	public boolean isExecutable(DocumentActionContext documentActionContext) {
	    return true;
	}
}