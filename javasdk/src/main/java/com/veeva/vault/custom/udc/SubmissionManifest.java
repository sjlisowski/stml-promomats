package com.veeva.vault.custom.udc;

import com.veeva.vault.sdk.api.core.*;
import com.veeva.vault.sdk.api.data.Record;
import com.veeva.vault.sdk.api.data.RecordBatchDeleteRequest;
import com.veeva.vault.sdk.api.data.RecordService;
import com.veeva.vault.sdk.api.document.DocumentService;
import com.veeva.vault.sdk.api.document.DocumentVersion;
import com.veeva.vault.sdk.api.query.QueryExecutionResult;

import java.util.Iterator;
import java.util.List;

/**
 * This module contains business logic relative to the Submission Manifest (submission_manifest__c) Object:
 *
 *   Public Static methods:
 *      IsSubmissionReady -- Checks if the Manifest is ready for Submission
 *      OnSubmit -- logic to execute when the Manifest is submitted to Regulatory Operations
 *      OnReturnToDraft -- logic to execute when the Manifest is returned to the Draft Status
 *      CheckAndClose -- close out the Submission Manifest if all materials have moved out of the "Pending Health
 *      Authority Submission" status
 */

@UserDefinedClassInfo
public class SubmissionManifest {

    /**
     *  Logic to execute when the Manifest is submitted to Regulatory Operations:
     *    - verify at least Submission Manifest Material (submission_manifest_material__c) is related;
     *    - verify no Materials have a Manifest ID for a Manifest other than the one being submitted;
     *    - verify all Materials are in status "Pending Health Authority Submission" (submit_to_health_authority__c)
     *    - populate the "Submission Manifest" field on all Materials related to the Manifest
     */
    public static Result OnSubmit(String manifestId) {

        Result result = IsSubmissionReady(manifestId);

        if (result.success == true) {
          List<String> documentVersionIdList = (List<String>) result.extra;
          setManifestReferenceOnDocuments(documentVersionIdList, manifestId);
        }

        return result;
    }

    /**
     * IsSubmissionReady - Tests whether a SubmissionManifest is ready to be submitted to Regulatory Operations.
     *
     *   If the test is successful (the Manifest is submission-ready), then the Result object contains a
     *   reference to a list of Document Versions IDs for the related material documents.
     *
     * @param manifestId - String.  The record ID of the Submission Manifest object record.
     * @return Result
     */
    public static Result IsSubmissionReady(String manifestId) {

        Result result = new Result();

        List<String> documentVersionIdList = getDocumentVersionIds(manifestId);

        /////////////////////////////////////////////////////////
        // Must have at least one related Material record...
        /////////////////////////////////////////////////////////
        if (documentVersionIdList.size() < 1) {
          result.success = false;
          result.message = "Submission Manifest must contain at least one Material";
          return result;
        }

        // multi-use variables...
        StringBuilder queryBuilder = new StringBuilder(1000);

        String versionIdContainsList = Util.vqlContains(documentVersionIdList);

        ///////////////////////////////////////////////////////////
        // No Materials are linked to a different Manifest...
        //////////////////////////////////////////////////////////
        {
            queryBuilder.setLength(0);
            queryBuilder
              .append("Select document_number__v,")
              .append("       document_material_submission_manifest__cr.name__v")
              .append("  from documents")
              .append(" where version_id contains ").append(versionIdContainsList)
              .append("   and material_submission_manifest__c != null ")
              .append("   and material_submission_manifest__c != '").append(manifestId).append("'");
            Iterator<QueryExecutionResult> queryResultIterator = QueryUtil.query(queryBuilder.toString())
              .streamResults()
              .iterator();
            if (queryResultIterator.hasNext()) {
                StringBuilder messageBuilder = new StringBuilder(200);
                messageBuilder.append("The following materials are included in another manifest:\n");
                while (queryResultIterator.hasNext()) {
                    QueryExecutionResult qresult = queryResultIterator.next();
                    String documentNumber = qresult.getValue("document_number__v", ValueType.STRING);
                    String manifestName = qresult.getValue("document_material_submission_manifest__cr.name__v", ValueType.STRING);
                    messageBuilder.append(documentNumber).append(" in ").append(manifestName).append("; ");
                }
                messageBuilder.append("\n");
                result.success = false;
                result.message = messageBuilder.toString();
                return result;
            }
        }

        ///////////////////////////////////////////////////////////////////////////////
        // Verify all Materials in status "Pending Health Authority Submission"...
        ///////////////////////////////////////////////////////////////////////////////
        {
            queryBuilder.setLength(0);
            queryBuilder
              .append("select id from documents")
              .append(" where version_id contains ").append(versionIdContainsList)
              .append("   and toName(status__v) != 'submit_to_health_authority__c'");
            long queryCount = QueryUtil.queryCount(queryBuilder.toString());
            if (queryCount > 0) {
              result.success = false;
              result.message = "All Materials must be in status \"Pending Health Authority Submission\".";
              return result;
            }
        }

        result.success = true;
        result.extra = documentVersionIdList;

        return result;
    }

    /**
     * Logic to execute when a Manifest is returned to Draft status.
     *   - Blank out the "Submission Manifest" field on all related Material documents
     *
     * @param manifestId
     */
    public static void OnReturnToDraft(String manifestId) {
        List<String> documentVersionIdList = getDocumentVersionIds(manifestId);
        // clear the reference to this manifest from the related documents...
        setManifestReferenceOnDocuments(documentVersionIdList, null);
    }

    /**
     * Check if the Manifest can be closed (actually deleted).
     * Return true if the Manifest was closed (deleted).
     * Return false if the Submission Manifest is not ready to be closed.  This is NOT an error state.
     *
     * This logic depends on the following configuration elements in Vault:
     *   - Submission Manifest object: the deletions rule for child records is set to "cascade"
     *   - Material document lifecycle: LCS "Approved for Distribution": Entry Action exists to blank
     *     out the "Submission Manifest" object reference field (material_submission_manifest__c)
     *   - Compliance Package document lifecycle: LCS "Submission Complete": Entry Action exists to
     *     blank out the "Submission Manifest" object reference field (submission_manifest__c)
     *
     * @param manifestId -- Submission Manifest record ID
     * @return
     */
    public static Result CheckAndClose(String manifestId) {

        Result result = new Result();

        String recordState = QueryUtil.queryOne(
          "select state__v from submission_manifest__c where id = '"+manifestId+"'"
        ).getValue("state__v", ValueType.STRING);

        if (!recordState.equals("submission_requested_state__c")) {
          result.success = false;
          result.message = "Submission Manifest record must be in status 'Submission Requested'";
          return result;
        }

        List<String> documentVersionIdList = getDocumentVersionIds(manifestId);

        if (documentVersionIdList.size() == 0) {  // this should never happen, but just in case
          result.success = false;
          result.message = "No documents were included in this Submission Manifest.";
          return result;
        }

        long queryCount = QueryUtil.queryCount(
          "select id" +
          "  from documents" +
          " where version_id contains " + Util.vqlContains(documentVersionIdList) +
          "   and toName(status__v) != 'approved_for_distribution__c'" +
          "   and toName(status__v) != 'awaiting_response_from_health_authority__c'"
        );

        if (queryCount > 0) {
            result.success = false;
            result.message = "Submission is not complete for at least 1 document.";
            return result;
        }

        RecordService recordService = ServiceLocator.locate(RecordService.class);
        Record record = recordService.newRecordWithId("submission_manifest__c", manifestId);
        RecordBatchDeleteRequest deleteRequest = recordService
          .newRecordBatchDeleteRequestBuilder()
          .withRecords(VaultCollections.asList(record))
          .build();
        recordService.batchDeleteRecords(deleteRequest)
          .onErrors(errors -> {
              errors.stream().findFirst().ifPresent(error -> {
                String errMsg = error.getError().getMessage();
                throw new RollbackException(
                  ErrorType.OPERATION_FAILED,
                  "Failed to delete Submission Manifest " + manifestId + " due to: " + errMsg
                );
              });
          })
          .execute();

        result.success = true;

        return result;
    }

    // link or un-link the material document to/from the manifest...
    private static void setManifestReferenceOnDocuments(List<String> documentVersionIdList, String manifestId) {

        DocumentService documentService = ServiceLocator.locate(DocumentService.class);
        List<DocumentVersion> documentVersions = VaultCollections.newList();

        List<String> manifestReference = VaultCollections.newList();
        if (manifestId != null) {
          manifestReference.add(manifestId);
        }

        Iterator<String> iterator = documentVersionIdList.iterator();
        while(iterator.hasNext()) {
            String documentVersionId = iterator.next();
            DocumentVersion documentVersion = documentService.newVersionWithId(documentVersionId);
            documentVersion.setValue("material_submission_manifest__c", manifestReference);
            documentVersions.add(documentVersion);
        }
        documentService.saveDocumentVersions(documentVersions);
    }

    // Return a list of the VersionId's for the documents referenced in the
    // related Submission Manifest Material object records.
    private static List<String> getDocumentVersionIds(String manifestId) {

        List<String> documentVersionIds = VaultCollections.newList();

        Iterator<QueryExecutionResult> iter = QueryUtil.query(
          "select document__c" +
          "  from submission_manifest_material__c" +
          " where submission_manifest__c = '"+manifestId+"'"
        ).streamResults().iterator();

        while (iter.hasNext()) {
            QueryExecutionResult result = iter.next();
            String documentVersionId = result.getValue("document__c", ValueType.STRING);
            documentVersionIds.add(documentVersionId);
        }

        return documentVersionIds;
    }
}