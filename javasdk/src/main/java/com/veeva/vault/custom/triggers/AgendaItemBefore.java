package com.veeva.vault.custom.triggers;

import com.veeva.vault.custom.udc.QueryUtil;
import com.veeva.vault.custom.udc.Util;
import com.veeva.vault.sdk.api.core.TriggerOrder;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.data.*;
import com.veeva.vault.sdk.api.query.QueryExecutionResult;

import java.math.BigDecimal;
import java.util.List;

/**
 * This trigger updates the project_manager__c field and sets the topic__c field to the Document Number if a Document
 * is selected in the document__c field.
 */

@RecordTriggerInfo(
  object = "agenda_item__c",
  events = {
    RecordEvent.BEFORE_INSERT,
    RecordEvent.BEFORE_UPDATE

  },
  order = TriggerOrder.NUMBER_1
)
public class AgendaItemBefore implements RecordTrigger {

    public void execute(RecordTriggerContext recordTriggerContext) {

      List<RecordChange> recordChanges = recordTriggerContext.getRecordChanges();

      if (recordChanges.size() > 1) {
        return; // This trigger supports single-record operations only (but DON'T throw and exception)
      }

      RecordChange inputRecord = recordChanges.get(0);

      RecordEvent recordEvent = recordTriggerContext.getRecordEvent();

      if (recordEvent == RecordEvent.BEFORE_INSERT) {

        Record newRecord = inputRecord.getNew();
        BigDecimal docId = newRecord.getValue("document_unbound__c", ValueType.NUMBER);
        if (docId != null) {
          updateDocumentInfo(newRecord, docId);
        }

      } else if (recordEvent == RecordEvent.BEFORE_UPDATE) {

        Record newRecord = inputRecord.getNew();
        Record oldRecord = inputRecord.getOld();

        BigDecimal docIdNew = newRecord.getValue("document_unbound__c", ValueType.NUMBER);
        BigDecimal docIdOld = oldRecord.getValue("document_unbound__c", ValueType.NUMBER);

        if (
          (docIdOld == null && docIdNew != null) ||
          (docIdOld != null && docIdNew != null && !docIdNew.equals(docIdOld))
        ) {
          updateDocumentInfo(newRecord, docIdNew);
        } else if (docIdOld != null && docIdNew == null) {
          updateDocumentInfo(newRecord, null);
        }

      }

    }  // end execute()

    /*********************************************************************************
       This method updates the topic__c field with the Document's document_number__v
       and the project_owner__c field with the name of the Document's Project Owner.
     *********************************************************************************/
    private void updateDocumentInfo(Record newRecord, BigDecimal docId) {

      if (docId == null) {
        //clear out the PM field but leave topic__c alone
        newRecord.setValue("project_owner__c", null);
        return;
      }

      int intDocId = docId.intValue();

      QueryExecutionResult queryResult = QueryUtil.queryOne(
        "select document_number__v from documents where id = " + intDocId
      );
      String documentNumber = queryResult.getValue("document_number__v", ValueType.STRING);
      newRecord.setValue("topic__c", documentNumber);

      String projectOwner = Util.getUserInDocumentRole(intDocId, "project_manager__c");
      String documentOwner =  Util.getUserInDocumentRole(intDocId, "owner__v");
      if (projectOwner != null) {
        newRecord.setValue("project_owner__c", projectOwner);
      }
      if (projectOwner == null || !documentOwner.equals(projectOwner)) {
        newRecord.setValue("document_owner__c", documentOwner);
      }
    }
}

