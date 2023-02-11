package com.veeva.vault.custom.udc;

import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.UserDefinedClassInfo;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.data.Record;
import com.veeva.vault.sdk.api.data.RecordService;
import com.veeva.vault.sdk.api.document.DocumentService;
import com.veeva.vault.sdk.api.document.DocumentVersion;
import com.veeva.vault.sdk.api.query.QueryExecutionResult;

import java.time.LocalDate;
import java.util.Iterator;
import java.util.List;

/**
 * This module contains business logic relative to Compliance Package binders:
 *
 *   - Generate2253Comments - compiles material-level information into a comments field for
 *       integration into the 2253 form
 *   - SetMaterialSubmissionDates - copy the value of the Submission Date field on the binder to
 *       the Submission Date field on each of the Materials contained in the Binder
 */

@UserDefinedClassInfo
public class CompliancePackage {

  /**
   *   - Generate2253Comments - compiles material-level information into a comments field for
   *         integration into the 2253 form
   *
   *   - The comments are stored in a record of object "Submission Comments".
   *   - The Compliance Package Binder has an object reference field that references this record.
   *   - If the object reference field on the Binder is null, this module creates the object record.
   *
   *   - The object "Submission Comments" has these fields:
   *     - comments__c, LongText(10,000)
   *     - comments_locked__c, Yes/No
   *
   *   - The user can bypass the processing in this module by setting the comments_locked__c field to "Yes"
   *
   * @param binderId, String
   */
  public static void Generate2253Comments(String binderId) {

      String commentsRecordId;
      Boolean commentsAreLocked;

      // Check if the Binder has a "Submission Comments" object reference value ...

      List<String> commentsRefField = QueryUtil.queryOne(
        "select 2253_comments__c from documents where id = " + binderId
        ).getValue("2253_comments__c", ValueType.REFERENCES);
      if (commentsRefField!= null) {
        commentsRecordId = commentsRefField.get(0);
      } else {
        commentsRecordId = null;
      }

      // Determine if comments are locked ...

      if (commentsRecordId == null) {
        commentsAreLocked = Boolean.valueOf(false);
      } else {
        commentsAreLocked = QueryUtil.queryOne(
          "select comments_locked__c from submission_comments__c where id = '"+commentsRecordId+"'"
        ).getValue("comments_locked__c", ValueType.BOOLEAN);
        if (commentsAreLocked == null) {
          commentsAreLocked = Boolean.valueOf(false);
        }
      }

      if (commentsAreLocked.booleanValue() == true) {
        return;
      }

      // Compile data across all comments ...

      StringBuilder commentsSB = new StringBuilder(10000); // The Comments object field is Long Text (10,000)

    Iterator<QueryExecutionResult> iter = QueryUtil.query(
      "select document__sysr.version_id," +
        "  document__sysr.material_id__v," +
        "  document__sysr.distribution_details__c," +
        "  document__sysr.replacement_material__c," +
        "  document__sysr.previous_material_id__c," +
        "  document__sysr.previous_material_name__c," +
        "  document__sysr.previous_material_submission_date__c" +
        "  from binder_node__sys" +
        " where binder__sysr.id = " + binderId +
        "   and type__sys = 'document__sys'" +
        "   and toName(document__sysr.type__v) = 'material__c'"
    ).streamResults().iterator();

      while (iter.hasNext()) {
        QueryExecutionResult result = iter.next();
        String materialId = result.getValue("document__sysr.material_id__v", ValueType.STRING);
        String distributionDetails = result.getValue("document__sysr.distribution_details__c", ValueType.STRING);
        Boolean replacementMaterial = result.getValue("document__sysr.replacement_material__c", ValueType.BOOLEAN);
        if (replacementMaterial == null) replacementMaterial = Boolean.valueOf(false);
        String prevMaterialId = result.getValue("document__sysr.previous_material_id__c", ValueType.STRING);
        String prevMaterialName = result.getValue("document__sysr.previous_material_name__c", ValueType.STRING);
        LocalDate prevSubmissionDate = result.getValue("document__sysr.previous_material_submission_date__c", ValueType.DATE);

        if (distributionDetails == null && replacementMaterial.booleanValue() == false) {
          continue;
        }

        commentsSB.append(materialId).append(":\n");

        if (distributionDetails != null & distributionDetails.length() > 0) {
          commentsSB.append(distributionDetails).append("\n");
        }

        if (replacementMaterial.booleanValue() == true) {
          commentsSB
            .append("Replaces: ")
            .append(prevMaterialId)
            .append(" \"").append(prevMaterialName).append("\" ")
            .append("submitted on ").append(prevSubmissionDate.toString()).append("\n");
        }

        commentsSB.append("\n");
        
      }  // end while()

      if (commentsSB.length() == 0) {
        return;  // this is an unlikely scenario, but just in case
      }

      RecordService recordService = ServiceLocator.locate(RecordService.class);

      if (commentsRecordId == null) {
        // create new record and link to the Binder
        Record newRecord = recordService.newRecord("submission_comments__c");
        newRecord.setValue("comments__c", commentsSB.toString());
        newRecord.setValue("comments_locked__c", Boolean.FALSE);
        newRecord.setValue("binder_id__c", binderId);
        Util.saveRecord(newRecord);
      } else {
        // update record comments
        Record updateRecord = recordService.newRecordWithId("submission_comments__c", commentsRecordId);
        updateRecord.setValue("comments__c", commentsSB.toString());
        Util.saveRecord(updateRecord);
      }

    }

  /**
   *  SetMaterialSubmissionDates - copy the value of the Submission Date field on the binder
   *     to the Submission Date field on each of the Materials contained in the Binder.
   *
   * @param binderId, String
   */
  public static void SetMaterialSubmissionDates(String binderId) {

      DocumentService documentService = ServiceLocator.locate((DocumentService.class));
      List<DocumentVersion> docVersionList = VaultCollections.newList();

      QueryExecutionResult submissionDateResult = QueryUtil.queryOne(
        "select submission_date__c from documents where id = " + binderId
      );
      LocalDate submissionDate = submissionDateResult.getValue("submission_date__c", ValueType.DATE);

      Iterator<QueryExecutionResult> iter = QueryUtil.query(
        "select document__sysr.version_id" +
          "  from binder_node__sys" +
          " where binder__sysr.id = " + binderId +
          "   and type__sys = 'document__sys'" +
          "   and toName(document__sysr.type__v) = 'material__c'"
      ).streamResults().iterator();

      while (iter.hasNext()) {
        QueryExecutionResult result = iter.next();;
        String docVersionId = result.getValue("document__sysr.version_id", ValueType.STRING);
        DocumentVersion documentVersion = documentService.newVersionWithId(docVersionId);
        documentVersion.setValue("submission_date__c", submissionDate);
        docVersionList.add(documentVersion);
      }

      if (docVersionList.size() > 0) {
        documentService.saveDocumentVersions(docVersionList);
      }

    }

}