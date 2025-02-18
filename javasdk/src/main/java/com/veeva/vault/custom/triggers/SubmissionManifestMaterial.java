package com.veeva.vault.custom.triggers;

import com.veeva.vault.custom.udc.DocVersionIdParts;
import com.veeva.vault.custom.udc.ErrorType;
import com.veeva.vault.custom.udc.QueryUtil;
import com.veeva.vault.custom.udc.Util;
import com.veeva.vault.sdk.api.core.RollbackException;
import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.data.*;
import com.veeva.vault.sdk.api.query.QueryExecutionResult;

import java.util.Iterator;
import java.util.List;

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
      String manifestId = getManifestId(record);

      checkDocumentIncludedOnAnotherManifest(recordId, materialId, manifestId); //throws exception

      RecordService recordService = ServiceLocator.locate(RecordService.class);
      Record updateRecord = recordService.newRecordWithId(record.getObjectName(), recordId);
      String docVersionId = record.getValue("document__c", ValueType.STRING);
      int docId = DocVersionIdParts.id(docVersionId);

      updateRecord.setValue("material_id__c", materialId);
      updateRecord.setValue("project_owner__c", Util.getUserInDocumentRole(docId, "project_manager__c"));

      Util.saveRecord(updateRecord);

    }

    // Check if the document, identified by the material id, is attached to another manifest
    // having the same Product.  If so, raise an exception.
    // This method is executed AFTER update/insert, so the current record is queryable.
    private void checkDocumentIncludedOnAnotherManifest(
      String recordId,   // record ID of the submission_manifest_material__c record that's being inserted/updated
      String materialId,  // materialId (i.e. docnbr-v#) of the document selected on the current record
      String thisManifestId  // manifest to which the current record is attached
    ) {
//      long queryCount = QueryUtil.queryCount(
//        "select id from submission_manifest_material__c" +
//        " where material_id__c = '"+materialId+"'" +
//        "   and id != '"+recordId+"'"
//      );

      Iterator<QueryExecutionResult> iter = QueryUtil.query(
        "select submission_manifest__c from submission_manifest_material__c" +
        " where material_id__c = '"+materialId+"'" +
        "   and id != '"+recordId+"'"
      ).streamResults().iterator();

      List<String> submissionManifestIDs = VaultCollections.newList();
      while (iter.hasNext()) {
        QueryExecutionResult result = iter.next();
        submissionManifestIDs.add(result.getValue("submission_manifest__c", ValueType.STRING));
      }

      if (submissionManifestIDs.size() > 0) {

        String productId = QueryUtil.queryOne(
          "select product__c from submission_manifest__c where id = '"+thisManifestId+"'"
        ).getValue("product__c", ValueType.STRING);

        long queryCount = QueryUtil.queryCount(
          "select id from submission_manifest__c" +
          " where product__c = '"+productId+"'" +
          "   and id contains " + Util.vqlContains(submissionManifestIDs)
        );

        if (queryCount > 0) {
          throw new RollbackException(ErrorType.OPERATION_DENIED,
            "This material was already included on this or another Manifest with the same Product."
          );
        }
        
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

    // Material ID is the document number plus the material version, e.g. "ELZ-00002-v2"
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

    private String getManifestId(Record record) {
      return record.getValue("submission_manifest__c", ValueType.STRING);
    }
}

