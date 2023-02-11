package com.veeva.vault.custom.jobs;

import com.veeva.vault.custom.udc.QueryUtil;
import com.veeva.vault.custom.udc.Util;
import com.veeva.vault.custom.udc.VaultAPI;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.job.*;
import com.veeva.vault.sdk.api.query.QueryExecutionResult;

import java.util.Iterator;
import java.util.List;

/*
 *  This Job will move all Material documents in a completed Compliance Package to the "Approved for Distribution"
 *  status (or the "Awaiting Health Authority Response" status, if applicable).
 */
  
  @JobInfo(adminConfigurable = true)
  public class CompliancePackageCompletion implements Job {

    private static final String UserActionAFD = "approved_for_distribution__c";
    private static final String UserActionAwait = "awaiting_response_from_health_authority__c";

    public JobInputSupplier init(JobInitContext jobInitContext) {

      JobLogger logger = jobInitContext.getJobLogger();

      String binderId = jobInitContext.getJobParameter("binderId", JobParamValueType.STRING);
      logger.log("Processing Compliance Package " + binderId);

      List<JobItem> jobItems = VaultCollections.newList();

      Iterator<QueryExecutionResult> iter = QueryUtil.query(
        "select document__sysr.version_id," +
        "       toName(document__sysr.submission_type__c)" +
        "  from binder_node__sys" +
        " where binder__sysr.id = " + binderId +
        "   and type__sys = 'document__sys'" +
        "   and toName(document__sysr.type__v) = 'material__c'" +
        "   and toName(document__sysr.status__v) = 'submit_to_health_authority__c'"
      ).streamResults().iterator();

      while (iter.hasNext()) {
        QueryExecutionResult result = iter.next();
        String docVersionId = result.getValue("document__sysr.version_id", ValueType.STRING);
        String submissionType = Util.getSinglePicklistValue(
          result.getValue("document__sysr.submission_type__c", ValueType.PICKLIST_VALUES)
        );
        if (!isValidSubmissionType(submissionType)) {
          submissionType = "fda_2253__c"; // hyper caution to make sure we have a valid submission type
        }
        JobItem jobItem = jobInitContext.newJobItem();
        jobItem.setValue("docVersionId", docVersionId);
        jobItem.setValue("submissionType", submissionType);
        jobItems.add(jobItem);
      }

      return jobInitContext.newJobInput(jobItems);
    }

    public void process(JobProcessContext jobProcessContext) {

      JobLogger logger = jobProcessContext.getJobLogger();
      VaultAPI vaultAPI = new VaultAPI("local_connection__c", logger);

      List<JobItem> items = jobProcessContext.getCurrentTask().getItems();

      int errorCount = 0;

      for (JobItem jobItem : items) {
        String docVersionId = jobItem.getValue("docVersionId", JobValueType.STRING);
        String submissionType = jobItem.getValue("submissionType", JobValueType.STRING);
        String actionLabel = submissionType.equals("fda_2253_advisory_comment__c") ?
          UserActionAwait : UserActionAFD;
        logger.log("Moving " + docVersionId + " to status " + actionLabel);
        vaultAPI.initiateDocumentUserActionLabel(docVersionId, actionLabel);
        if (vaultAPI.failed()) {
          String errorType = vaultAPI.getErrorType();
          String errorMsg = vaultAPI.getErrorMessage();
          logger.log(docVersionId + " failed with " + errorType + ": " + errorMsg);
          errorCount += 1;
        }
       }

       JobTask task = jobProcessContext.getCurrentTask();
       TaskOutput taskOutput = task.getTaskOutput();

       if (errorCount > 0) {
         taskOutput.setState(TaskState.ERRORS_ENCOUNTERED);
       } else {
         taskOutput.setState(TaskState.SUCCESS);
       }

    }

    public void completeWithSuccess(JobCompletionContext jobCompletionContext) {
       JobLogger logger = jobCompletionContext.getJobLogger();
       logger.log("All tasks completed successfully");
    }

    public void completeWithError(JobCompletionContext jobCompletionContext) {
       JobResult result = jobCompletionContext.getJobResult();

       JobLogger logger = jobCompletionContext.getJobLogger();
       logger.log("completeWithError: " + result.getNumberFailedTasks() + "tasks failed out of " + result.getNumberTasks());

       List<JobTask> tasks = jobCompletionContext.getTasks();
       for (JobTask task : tasks) {
           TaskOutput taskOutput = task.getTaskOutput();
           if (TaskState.ERRORS_ENCOUNTERED.equals(taskOutput.getState())) {
              logger.log(task.getTaskId() + " failed with error message " + taskOutput.getValue("firstError", JobValueType.STRING));
           }
       }
    }

    private boolean isValidSubmissionType(String submissionType) {
      return (
        submissionType != null &&
        (submissionType.equals("fda_2253__c") || submissionType.equals("fda_2253_advisory_comment__c"))
      );
    }

  }