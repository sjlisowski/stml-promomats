package com.veeva.vault.custom.triggers;

import com.veeva.vault.custom.udc.DocVersionIdParts;
import com.veeva.vault.custom.udc.ErrorType;
import com.veeva.vault.custom.udc.QueryUtil;
import com.veeva.vault.custom.udc.Util;
import com.veeva.vault.sdk.api.core.RollbackException;
import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.data.*;

/**
 * This trigger performs the following functions:
 *    - Don't allow a Document in more than one Submission Manifest
 *    - Don't allow certain updates (insert, delete, update material) if the Manifest is
 *      submitted to Regulatory Operations ("Submission Requested" or "Submission Pending" status)
 *    - Populate the read-only "Material ID" field on the record being inserted or updated
 */

@RecordTriggerInfo(
  object = "submission_manifest_material__c",
  events = {
    RecordEvent.BEFORE_INSERT,
    RecordEvent.BEFORE_DELETE,
    RecordEvent.AFTER_INSERT,
    RecordEvent.AFTER_UPDATE
  }
)
public class SubmissionManifestMaterial implements RecordTrigger {

    public void execute(RecordTriggerContext recordTriggerContext) {

      // Bulk updates are not expected, so the loop logic is safe...

      for (RecordChange inputRecord : recordTriggerContext.getRecordChanges()) {

        RecordEvent recordEvent = recordTriggerContext.getRecordEvent();

        if (recordEvent == RecordEvent.BEFORE_INSERT) {
          Record record = inputRecord.getNew();
          checkManifestNotSubmitted(record);
        }

        if (recordEvent == RecordEvent.BEFORE_DELETE) {
          Record record = inputRecord.getOld();
          checkManifestNotSubmitted(record);
        }

        // Because the "document__c" field is an unbound document reference field, access
        // to the field is not available until AFTER Insert or Update

        if (recordEvent == RecordEvent.AFTER_INSERT) {
          processRecordInsertUpdate(inputRecord.getNew());
        }

        if (recordEvent == RecordEvent.AFTER_UPDATE) {
          String newDocumentVersionId = getDocumentVersionId(inputRecord.getNew());
          String oldDocumentVersionId = getDocumentVersionId(inputRecord.getOld());
          if (!newDocumentVersionId.equals(oldDocumentVersionId)) {
            processRecordInsertUpdate(inputRecord.getNew());
          }
         }

      }  // end for
    	
    }

    // Process the inserted or updated record (AFTER insert or Update)
    private void processRecordInsertUpdate(Record record) {

      String recordId = getRecordId(record);
      String materialId = getMaterialId(getDocumentVersionId(record));

      checkDocumentIncludedOnAnotherManifest(recordId, materialId); //throws exception

      RecordService recordService = ServiceLocator.locate(RecordService.class);
      Record updateRecord = recordService.newRecordWithId(record.getObjectName(), recordId);
      String docVersionId = record.getValue("document__c", ValueType.STRING);
      int docId = DocVersionIdParts.id(docVersionId);

      updateRecord.setValue("material_id__c", materialId);
      updateRecord.setValue("project_owner__c", Util.getUserInDocumentRole(docId, "project_manager__c"));

      Util.saveRecord(updateRecord);

    }

    // Is the document, identified by the material id, attached to a different manifest?
    private void checkDocumentIncludedOnAnotherManifest(String recordId, String materialId) {
      long queryCount = QueryUtil.queryCount(
        "select id from submission_manifest_material__c" +
        " where material_id__c = '"+materialId+"'" +
        "   and id != '"+recordId+"'"
      );
      if (queryCount > 0) {
        throw new RollbackException(ErrorType.OPERATION_DENIED,
          "This material was already included on this or another Manifest."
        );
      }
    }

    // Rollback if the User is trying to make an unsupported change while the status
    // of the manifest is "Submission Requested"
    private void checkManifestNotSubmitted(Record record) {
      String manifestId = record.getValue("submission_manifest__c", ValueType.STRING);
      String lifecycleState = QueryUtil.queryOne(
        "select state__v from submission_manifest__c where id = '"+manifestId+"'"
      ).getValue("state__v", ValueType.STRING);
      if (lifecycleState.equals("submission_requested_state__c") ||
          lifecycleState.equals("submission_pending_state__c")) {
        throw new RollbackException(ErrorType.OPERATION_DENIED,
          "This change is not allowed at this time.  Your Submission Manifest must be in \"Draft\" status in order to make this change."
        );
      }
    }

    private String getMaterialId(String documentVersionId) {
      return QueryUtil.queryOne(
        "select material_id__v from documents where version_id = '"+documentVersionId+"'"
      ).getValue("material_id__v", ValueType.STRING);
    }

    private String getRecordId(Record record) {
      return record.getValue("id", ValueType.STRING);
    }

    private String getDocumentVersionId(Record record) {
      return record.getValue("document__c", ValueType.STRING);
    }
}

