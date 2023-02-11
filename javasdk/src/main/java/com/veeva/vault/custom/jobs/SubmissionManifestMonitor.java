package com.veeva.vault.custom.jobs;

import com.veeva.vault.custom.udc.QueryUtil;
import com.veeva.vault.custom.udc.Result;
import com.veeva.vault.custom.udc.SubmissionManifest;
import com.veeva.vault.custom.udc.VaultAPI;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.job.*;
import com.veeva.vault.sdk.api.query.QueryExecutionResult;

import java.util.Iterator;
import java.util.List;

/*
    This Job will run as a scheduled Operational Task.

    Two functions are performed:

      - Move "Submission Pending" manifests to the "Submission Ready" status when the Manifest is ready for submission
         to Regulatory Ops
      - Move "Submission Ready" manifests to the "Closed" status when all related Materials have been submitted.
      
 */

@JobInfo(adminConfigurable = true)
public class SubmissionManifestMonitor implements Job {

    public JobInputSupplier init(JobInitContext jobInitContext) {

        JobLogger logger = jobInitContext.getJobLogger();

        List<JobItem> jobItems = VaultCollections.newList();

        Iterator<QueryExecutionResult> iter = QueryUtil.query(
          "select id, state__v from submission_manifest__c " +
          " where state__v contains ('submission_pending_state__c', 'submission_requested_state__c')"
        ).streamResults().iterator();

        while (iter.hasNext()) {
          QueryExecutionResult qResult = iter.next();
          String recordId = qResult.getValue("id", ValueType.STRING);
          String recordStatus = qResult.getValue("state__v", ValueType.STRING);
          JobItem jobItem = jobInitContext.newJobItem();
          jobItem.setValue("recordId", recordId);
          jobItem.setValue("recordStatus", recordStatus);
          jobItems.add(jobItem);
        }

        return jobInitContext.newJobInput(jobItems);
    }

    public void process(JobProcessContext jobProcessContext) {

        JobLogger logger = jobProcessContext.getJobLogger();

        List<JobItem> jobItems = jobProcessContext.getCurrentTask().getItems();

        int errorCount = 0;

        for (JobItem jobItem : jobItems) {
          String recordId = jobItem.getValue("recordId", JobValueType.STRING);
          String recordStatus = jobItem.getValue("recordStatus", JobValueType.STRING);
          TaskState taskState = null;
          if (recordStatus.equals("submission_pending_state__c")) {
            taskState = processPendingManifest(recordId, jobProcessContext);
          } else if (recordStatus.equals("submission_requested_state__c")) {
            taskState = processSubmittedManifest(recordId, jobProcessContext);
          } else {
            logger.log("Invalid status: " + recordStatus);  // this should never happen
            errorCount++;
          }
          if (taskState == TaskState.ERRORS_ENCOUNTERED) {
            errorCount++;
          }
        }

        JobTask task = jobProcessContext.getCurrentTask();
        TaskOutput taskOutput = task.getTaskOutput();

        if (errorCount == 0) {
            taskOutput.setState(TaskState.SUCCESS);
        } else {
            taskOutput.setState(TaskState.ERRORS_ENCOUNTERED);
        }
    }

    public void completeWithSuccess(JobCompletionContext jobCompletionContext) {
        JobLogger logger = jobCompletionContext.getJobLogger();
        logger.log("All tasks completed successfully");
    }

    public void completeWithError(JobCompletionContext jobCompletionContext) {
        JobResult result = jobCompletionContext.getJobResult();
        JobLogger logger = jobCompletionContext.getJobLogger();
        logger.log("completeWithError: " + result.getNumberFailedTasks() + " tasks failed out of " + result.getNumberTasks());
    }

    // Process a Submission Manifest object record in the "Submission Pending" status.
    // Check if the Submission Manifest is ready for submission to Regulatory Operations.
    // Move the record to the "Submission Requested" status if it is ready for submission.
    private TaskState processPendingManifest(String recordId, JobProcessContext jobProcessContext) {

      JobLogger logger = jobProcessContext.getJobLogger();

      logger.log("Checking submission readiness for " + recordId);

      Result result = SubmissionManifest.IsSubmissionReady(recordId);

      if (result.success == false) {
        logger.log(recordId + " is not ready for submission due to: " + result.message);
        return TaskState.SUCCESS; // this is not an error
      }

      logger.log("Attempting to execute workflow \"Submit to Regulatory Operations\" for " + recordId);

      VaultAPI vaultAPI = new VaultAPI("local_connection__c");

      String actionName = vaultAPI.getObjectUserActionName(
        "submission_manifest__c", recordId, "Submit to Regulatory Operations"
      );

      if (vaultAPI.failed()) {
        String errorType = vaultAPI.getErrorType();
        String errorMsg = vaultAPI.getErrorMessage();
        logger.log(recordId + ": an error occured: " + errorType + ": " + errorMsg);
        return TaskState.ERRORS_ENCOUNTERED;
      }

      vaultAPI.initiateObjectRecordUserAction("submission_manifest__c", recordId, actionName);

      if (vaultAPI.failed()) {
        String errorType = vaultAPI.getErrorType();
        String errorMsg = vaultAPI.getErrorMessage();
        logger.log(recordId + ": an error occured: " + errorType + ": " + errorMsg);
        return TaskState.ERRORS_ENCOUNTERED;
      }

      logger.log("Successfully submitted manifest " + recordId);

      return TaskState.SUCCESS;
    }

  // Process a Submission Manifest object record in the "Submission Requested" status.
  // See SubmissionManifest.CheckAndClose() for processing details.
    private TaskState processSubmittedManifest(String recordId, JobProcessContext jobProcessContext) {

      JobLogger logger = jobProcessContext.getJobLogger();

      logger.log("Checking Submission Manifest " + recordId + " for closure.");

      Result result = SubmissionManifest.CheckAndClose(recordId);

]      if (result.success == false) {
        logger.log("Submission Manifest " + recordId + " not closed due to: " + result.message);
      } else {
        logger.log("Submission Manifest " + recordId + " closed");
      }

      return TaskState.SUCCESS;
    }

}
