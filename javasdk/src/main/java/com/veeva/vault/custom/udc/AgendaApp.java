package com.veeva.vault.custom.udc;

import com.veeva.vault.sdk.api.core.*;
import com.veeva.vault.sdk.api.data.RecordService;
import com.veeva.vault.sdk.api.query.QueryExecutionResult;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.List;

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

    /**
     *  Set Agenda records to inactive for Agendas whose meeting date is before Today.
     */
    public static void deactivatePastAgendas(Logger logger) {

      RecordService recordService = ServiceLocator.locate(RecordService.class);
      List<String> inactive__v = VaultCollections.asList("inactive__v");

      LocalDate dtToday = LocalDate.now();
      DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
      String strToday = dtToday.format(formatter);

      Iterator<QueryExecutionResult> iterator = QueryUtil.query(
        "select id, name__v " +
          "from agenda__c "+
         "where meeting_date__c < '"+strToday+"' " +
           "and status__v = 'active__v'"
      ).streamResults().iterator();

      if (! iterator.hasNext()) {
        logger.info("No past Agendas found.");
      }

      VaultAPI vapi = new VaultAPI("local_connection__c");
      String actionName = null;

      while (iterator.hasNext()) {
        QueryExecutionResult result = iterator.next();
        String id = result.getValue("id", ValueType.STRING);
        String name = result.getValue("name__v", ValueType.STRING);
        logger.info("Found agenda "+id+": '"+name+"'");
        if (actionName == null) {
          actionName = vapi.getObjectUserActionName("agenda__c", id, "Make Inactive");
        }
        vapi.initiateObjectRecordUserAction("agenda__c", id, actionName);
        if (vapi.failed()) {
          throw new RollbackException(
            ErrorType.OPERATION_FAILED,
            "id + \": an error occured: \" + errorType + \": \" + errorMsg"
          );
        }
      }  // end while()

    }
}