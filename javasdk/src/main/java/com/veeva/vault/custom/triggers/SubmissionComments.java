package com.veeva.vault.custom.triggers;

import com.veeva.vault.custom.udc.QueryUtil;
import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.data.*;
import com.veeva.vault.sdk.api.document.DocumentService;
import com.veeva.vault.sdk.api.document.DocumentVersion;

/**
 *
 * This trigger links this record to the Compliance Package Binder to which it belongs by populating
 * the Binder's "2253 Comments" (2253_comments__c) field with the ID of this record.
 *
 */

@RecordTriggerInfo(
  object = "submission_comments__c",
  events = {RecordEvent.AFTER_INSERT}
)
public class SubmissionComments implements RecordTrigger {

    public void execute(RecordTriggerContext recordTriggerContext) {

      DocumentService documentService = ServiceLocator.locate(DocumentService.class);

      // records of this object are not intended ever to be bulk inserted, so
      // the loop logic is safe

      for (RecordChange inputRecord : recordTriggerContext.getRecordChanges()) {

        Record newRecord = inputRecord.getNew();
        String recordId = newRecord.getValue("id", ValueType.STRING);
        String binderId = newRecord.getValue("binder_id__c", ValueType.STRING);

        String binderVersionId = QueryUtil.queryOne(
          "select version_id from documents where id = " + binderId
        ).getValue("version_id", ValueType.STRING);

        DocumentVersion binderVersion = documentService.newVersionWithId(binderVersionId);
        binderVersion.setValue("2253_comments__c", VaultCollections.asList(recordId));
        documentService.saveDocumentVersions(VaultCollections.asList(binderVersion));

      }
    	
    }
}

