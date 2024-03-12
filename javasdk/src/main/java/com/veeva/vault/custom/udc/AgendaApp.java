package com.veeva.vault.custom.udc;

import com.veeva.vault.sdk.api.core.UserDefinedClassInfo;
import com.veeva.vault.sdk.api.core.ValueType;

/**
 *  Methods needed to support the Review Agenda App.
 */

@UserDefinedClassInfo
public class AgendaApp {

    public static final String AGENDA_ID = "AgendaId";
    public static final String AGENDA_NAME = "AgendaName";
    public static final String AGENDA_MEETNG_TIME = "AgendaMeetingTime";
    public static final String AGENDA_ITEM_SEMAPHORE = "semaphore";
    
    public static String getAgendaMeetingTime(String agendaId) {
      return QueryUtil.queryOne(
        "select meeting_time__c from agenda__c where id = '"+agendaId+"'"
      ).getValue("meeting_time__c", ValueType.STRING);
    }

}