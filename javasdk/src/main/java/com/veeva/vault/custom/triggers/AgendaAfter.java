package com.veeva.vault.custom.triggers;

import com.veeva.vault.custom.udc.AgendaItemsList;
import com.veeva.vault.sdk.api.core.TriggerOrder;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.data.*;

import java.util.List;

@RecordTriggerInfo(
  object = "agenda__c",
  events = {RecordEvent.AFTER_UPDATE},
  order = TriggerOrder.NUMBER_1
)
public class AgendaAfter implements RecordTrigger {

    private static final String MeetingTimeFieldName = "meeting_time__c";

    public void execute(RecordTriggerContext recordTriggerContext) {

      List<RecordChange> recordChanges = recordTriggerContext.getRecordChanges();

      if (recordChanges.size() > 1) {
        return; // This trigger supports single-record operations only. But DO NOT throw an exception.
      }

      RecordChange inputRecord = recordChanges.get(0);

      Record newRecord = inputRecord.getNew();
      Record oldRecord = inputRecord.getOld();

      String agendaId = newRecord.getValue("id", ValueType.STRING);
      String newMeetingTime = newRecord.getValue(MeetingTimeFieldName, ValueType.STRING);
      String oldMeetingTime = oldRecord.getValue(MeetingTimeFieldName, ValueType.STRING);

      if (
           (oldMeetingTime != null && newMeetingTime == null) ||
           (oldMeetingTime == null && newMeetingTime != null) ||
           (newMeetingTime != null && !newMeetingTime.equals(oldMeetingTime))
         )
      {
        AgendaItemsList items = new AgendaItemsList(agendaId);
        items.updateStartEndTimes(newMeetingTime);
        items.saveChangedRecords();
      }

    }  // end execute()

}

