package com.veeva.vault.custom.udc;

import com.veeva.vault.sdk.api.core.UserDefinedClassInfo;

/**
 * This class can be used to return a result from a method, along with a message and extra data.
 */

@UserDefinedClassInfo
public class Result {
    
    public boolean success;
    public String message;
    public Object extra;

    public Result() {
      this.success = false;
      this.message = null;
      this.extra = null;
    }
}